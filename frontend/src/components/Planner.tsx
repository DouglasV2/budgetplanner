import { useEffect, useMemo, useRef, useState } from 'react';
import { generatePlan, getDesignSummary, getSavedPlan, listSavedPlans, replaceProduct, savePlan, sendPlanFeedback, setSavedPlanFavorite, trackProductClick } from '../api/client';
import type { DesignAssistant, FurnishingPlan, OptimizationGoal, PlanFeedback, PlannerInput, PlannerIntentAnalysis, Product, ReplacementChoice, Retailer, SavedPlanResponse } from '../types';
import { formatCurrency, retailersForMarket, roomLabels, styleLabels } from '../utils/planner';
import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';
import { detectMarketFromText, marketConfig } from '../markets';
import { PlannerForm } from './PlannerForm';
import { PlanResults, type QuickPlanAction } from './PlanResults';

const initialInput: PlannerInput = {
  prompt:
    'Imam 1500 € za dnevni boravak od 20 m² u Zagrebu. Želim svijetli i prozračni stil, kombiniraj IKEA i JYSK ako ima smisla. Trebam kauč, TV komodu, klub stolić, tepih i lampu.',
  budget: 1500,
  roomType: 'living-room',
  style: 'bright',
  location: 'Zagreb',
  size: 20,
  retailerMode: 'multi',
  selectedRetailers: ['IKEA', 'JYSK', 'Pevex', 'Emmezeta', 'Decathlon', 'Lesnina'],
  optimizationGoal: 'best-value',
  furnishingLevel: 'comfort',
  mustHaveCategories: [],
  alreadyHaveCategories: [],
  lockedProductIds: []
};

function mostUsedRetailer(plan?: FurnishingPlan): Retailer | undefined {
  if (!plan) return undefined;
  const counts = plan.items.reduce<Partial<Record<Retailer, number>>>((acc, item) => {
    acc[item.product.retailer] = (acc[item.product.retailer] ?? 0) + 1;
    return acc;
  }, {});

  return Object.entries(counts).sort((a, b) => b[1] - a[1])[0]?.[0] as Retailer | undefined;
}

function readSharedPlanIdFromUrl() {
  const match = window.location.pathname.match(/^\/plan\/([^/]+)$/);
  return match?.[1] ?? null;
}

function planSearchText(savedPlan: SavedPlanResponse) {
  return [
    savedPlan.plan.name,
    savedPlan.plan.label,
    savedPlan.plan.summary,
    roomLabels[savedPlan.input.roomType],
    styleLabels[savedPlan.input.style],
    savedPlan.plan.retailersUsed.join(' '),
    savedPlan.plan.items.map((item) => item.product.name).join(' ')
  ].join(' ').toLowerCase();
}

function formatSavedDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'spremljeno ranije';
  return new Intl.DateTimeFormat('hr-HR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric'
  }).format(date);
}

// Sprint 10.61: Saved Spaces — group a session's saved room-plans by "space" (e.g. "Moj dom"), let the user
// switch/create the active space, and "keep designing" a space (= start another room for the same home).
function SavedPlansInbox({
  plans,
  search,
  onSearchChange,
  onOpen,
  onFavorite,
  activeSpace,
  onActiveSpaceChange,
  onContinueSpace,
  defaultSpaceName
}: {
  plans: SavedPlanResponse[];
  search: string;
  onSearchChange: (value: string) => void;
  onOpen: (plan: SavedPlanResponse) => void;
  onFavorite: (plan: SavedPlanResponse) => void;
  activeSpace: string;
  onActiveSpaceChange: (name: string) => void;
  onContinueSpace: (name: string) => void;
  defaultSpaceName: string;
}) {
  const { t } = useLocale();
  const [newSpace, setNewSpace] = useState('');
  const filteredPlans = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return plans.slice(0, 24);
    return plans.filter((plan) => planSearchText(plan).includes(query)).slice(0, 24);
  }, [plans, search]);

  if (!plans.length) return null;

  const spaceOf = (plan: SavedPlanResponse) => plan.spaceName?.trim() || defaultSpaceName;
  const groups = new Map<string, SavedPlanResponse[]>();
  filteredPlans.forEach((plan) => {
    const key = spaceOf(plan);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(plan);
  });
  const spaceNames = Array.from(new Set([activeSpace, ...groups.keys()]));

  function createSpace() {
    const name = newSpace.trim();
    if (!name) return;
    onActiveSpaceChange(name);
    setNewSpace('');
  }

  return (
    <details className="saved-plans-inbox" open>
      <summary>
        <div>
          <span>{t('saved.title')}</span>
          <strong>{t('saved.countLine', { saved: plans.length, favorites: plans.filter((plan) => plan.favorite).length })}</strong>
        </div>
        <small>{t('saved.openLater')}</small>
      </summary>

      <div className="spaces-control">
        <span className="spaces-control-label">{t('spaces.designingFor')}</span>
        <div className="spaces-chips">
          {spaceNames.map((name) => (
            <button type="button" key={name} className={name === activeSpace ? 'space-chip active' : 'space-chip'} onClick={() => onActiveSpaceChange(name)}>
              {name}
            </button>
          ))}
          <input
            className="space-new-input"
            value={newSpace}
            placeholder={t('spaces.newPlaceholder')}
            aria-label={t('spaces.newPlaceholder')}
            onChange={(event) => setNewSpace(event.target.value)}
            onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); createSpace(); } }}
          />
        </div>
      </div>

      <div className="saved-plans-toolbar">
        <label>
          <span>{t('saved.search')}</span>
          <input value={search} onChange={(event) => onSearchChange(event.target.value)} placeholder={t('saved.search')} />
        </label>
      </div>

      {Array.from(groups.entries()).map(([spaceName, spacePlans]) => (
        <div className="space-group" key={spaceName}>
          <div className="space-group-head">
            <strong>{spaceName}</strong>
            <small>{t('saved.roomsCount', { count: spacePlans.length })}</small>
            <button type="button" className="space-continue" onClick={() => onContinueSpace(spaceName)}>{t('spaces.continue')}</button>
          </div>
          <div className="saved-plans-list">
            {spacePlans.map((savedPlan) => {
              const stores = savedPlan.plan.retailersUsed.join(' + ') || t('saved.noStores');
              return (
                <article className={savedPlan.favorite ? 'saved-plan-card favorite' : 'saved-plan-card'} key={savedPlan.id}>
                  <div>
                    <span>{savedPlan.favorite ? t('saved.favorite') : t('saved.saved')} · {formatSavedDate(savedPlan.createdAt)}</span>
                    <strong>{roomLabels[savedPlan.input.roomType]}</strong>
                    <small>{t('saved.metaLine', { price: formatCurrency(savedPlan.plan.total), count: savedPlan.plan.items.length, stores })}</small>
                  </div>
                  <div className="saved-plan-actions">
                    <button
                      type="button"
                      className={savedPlan.favorite ? 'favorite-toggle active' : 'favorite-toggle'}
                      onClick={() => onFavorite(savedPlan)}
                      aria-label={savedPlan.favorite ? t('saved.removeFavorite') : t('saved.addFavorite')}
                    >
                      {savedPlan.favorite ? t('saved.favoriteStar') : t('saved.markStar')}
                    </button>
                    <button type="button" onClick={() => onOpen(savedPlan)}>{t('saved.open')}</button>
                  </div>
                </article>
              );
            })}
          </div>
        </div>
      ))}
      {!filteredPlans.length && <p className="saved-empty">{t('saved.emptySearch')}</p>}
    </details>
  );
}

export function Planner() {
  const { market, config, setMarket, t } = useLocale();
  // Sprint 10.63: real auth. When the signed-in user changes (sign-in/out), the saved-plans inbox refetches so
  // the now account-owned plans (migrated from the guest session on first sign-in) appear.
  const { user, openSignIn } = useAuth();
  // The example prompt is localised: Croatian for HR, English for the other markets. We seed it once from
  // the active market's language (the user can then edit freely).
  const [input, setInput] = useState<PlannerInput>(() => ({ ...initialInput, prompt: t('planner.examplePrompt'), market }));
  // The example prompt is seeded from t() at mount, but a non-HR market's translations are lazy-loaded, so the
  // first seed can be the English fallback. Re-seed it once the language overlay arrives (t() identity changes
  // on langReady) — but only while the textarea still holds the seeded example, so a user's edits are never lost.
  const seededPromptRef = useRef<string>(input.prompt);
  const [plans, setPlans] = useState<FurnishingPlan[]>([]);
  const [savedPlans, setSavedPlans] = useState<SavedPlanResponse[]>([]);
  const [savedSearch, setSavedSearch] = useState('');
  const [generationCount, setGenerationCount] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [partialNotice, setPartialNotice] = useState<string | null>(null);
  const [design, setDesign] = useState<DesignAssistant | null>(null);
  const [analysis, setAnalysis] = useState<PlannerIntentAnalysis | null>(null);
  // Sprint 10.74 (C): the prompt the user actually typed, captured at submit (response.input.prompt is cleared on
  // the AI path). Used to show a gentle "I wasn't sure — describe a room + budget" nudge on low-confidence input.
  const [submittedPrompt, setSubmittedPrompt] = useState('');
  // Sprint 10.51: the separate "Rabljeno" (second-hand) suggestions — kept entirely out of every plan total.
  const [secondHand, setSecondHand] = useState<Product[]>([]);
  // Sprint 10.61: the active "space" (home) that new room-plans save into; default "Moj dom".
  const [activeSpace, setActiveSpace] = useState<string>(() => t('spaces.defaultName'));
  // Sprint 10.13 (#3): reversible "we picked your country from the prompt" note.
  const [marketNote, setMarketNote] = useState<string | null>(null);

  async function refreshSavedPlans() {
    try {
      const nextSavedPlans = await listSavedPlans();
      setSavedPlans(nextSavedPlans);
    } catch {
      // Saved plans are helpful, but the main planner should still work if this endpoint is unavailable.
    }
  }

  useEffect(() => {
    void refreshSavedPlans();
    // Refetch when the signed-in identity changes: after sign-in the inbox should show the account's plans
    // (including the ones migrated from this browser's guest session), and after sign-out the guest's again.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  // Sprint 10.13 (#3): keep the request's market in sync with the selected country so the backend
  // filters the catalog and the totals are formatted in that market's currency.
  useEffect(() => {
    setInput((current) => {
      if (current.market === market) return current;
      // Sprint 10.30: when the country changes, keep only the stores that exist in the new market and
      // fall back to that market's stores if none of the previous picks are available — so switching to
      // e.g. IT never leaves an HR-only store selected (which would yield an empty plan).
      const allowed = retailersForMarket(market);
      const kept = current.selectedRetailers.filter((retailer) => allowed.includes(retailer));
      return { ...current, market, selectedRetailers: kept.length ? kept : allowed };
    });
  }, [market]);

  // Re-seed the localised example prompt when the active language's overlay loads or the market changes.
  // Keep the ref update OUTSIDE the state updater — StrictMode double-invokes the updater in dev, and a ref
  // mutation inside it would make the second pass bail. Only replace while the textarea still holds the seed.
  useEffect(() => {
    const next = t('planner.examplePrompt');
    const prevSeed = seededPromptRef.current;
    seededPromptRef.current = next;
    setInput((current) => (current.prompt === prevSeed && current.prompt !== next ? { ...current, prompt: next } : current));
  }, [t]);

  useEffect(() => {
    const sharedPlanId = readSharedPlanIdFromUrl();
    if (!sharedPlanId) return;

    setIsLoading(true);
    getSavedPlan(sharedPlanId)
      .then((savedPlan) => {
        setInput(savedPlan.input);
        setPlans([savedPlan.plan]);
        setSecondHand([]);
        setNotice(t('planner.noticeLoaded'));
      })
      .catch(() => setError(t('planner.errorNotFound')))
      .finally(() => setIsLoading(false));
  }, []);

  async function runGeneration(nextInput: PlannerInput) {
    // Sprint 10.13 (#3): if the prompt names a country/city we support, auto-switch the market for
    // this plan and show a reversible note. The selector stays the source of truth (user can revert).
    let effectiveInput = nextInput;
    const detected = detectMarketFromText(nextInput.prompt);
    if (detected && detected !== market) {
      const cfg = marketConfig(detected);
      setMarket(detected);
      setMarketNote(
        cfg.lang === 'hr'
          ? `Tržište postavljeno na ${cfg.label} (iz tvog opisa). Promijeni gore ako nije točno.`
          : `Market set to ${cfg.label} (from your description). Change it above if needed.`
      );
      effectiveInput = { ...nextInput, market: detected };
    } else {
      setMarketNote(null);
    }

    setIsLoading(true);
    setError(null);
    setNotice(null);
    setPartialNotice(null);
    setDesign(null);
    setAnalysis(null);
    setSecondHand([]);

    try {
      const response = await generatePlan(effectiveInput);
      setSubmittedPrompt((effectiveInput.prompt ?? '').trim()); // before setInput clears it on the AI path
      setInput({ ...response.input, market: response.input.market ?? effectiveInput.market, lockedProductIds: response.input.lockedProductIds ?? effectiveInput.lockedProductIds ?? [] });
      setPlans(response.plans);
      setAnalysis(response.intentAnalysis ?? null);
      setSecondHand(response.secondHandSuggestions ?? []);
      const hasAnyItems = response.plans.some((plan) => plan.items.length > 0);
      if (!hasAnyItems) {
        setPartialNotice(t('planner.partialNone'));
      } else {
        setPartialNotice(response.partialPlan ? (response.catalogWarning ?? t('planner.partialBest')) : null);
      }
      setGenerationCount((count) => count + 1);
      // Sprint 10.8: fetch the design-assistant description. Non-blocking: if it fails the plan still shows.
      getDesignSummary(response)
        .then((summary) => setDesign(summary))
        .catch(() => setDesign(null));
    } catch (apiError) {
      setError(
        apiError instanceof Error
          ? apiError.message
          : t('planner.errorUnavailable')
      );
    } finally {
      setIsLoading(false);
    }
  }

  async function handleGenerate() {
    await runGeneration(input);
  }

  async function handleReplace(planId: string, productId: string, changeType: ReplacementChoice = 'similar') {
    const plan = plans.find((currentPlan) => currentPlan.id === planId);
    if (!plan) return;

    try {
      const updatedPlan = await replaceProduct(plan, input, productId, changeType);
      setPlans((currentPlans) => currentPlans.map((currentPlan) => (currentPlan.id === planId ? updatedPlan : currentPlan)));
    } catch (apiError) {
      setError(apiError instanceof Error ? apiError.message : t('planner.errorReplace'));
    }
  }

  function handleToggleLock(productId: string) {
    setInput((currentInput) => {
      const lockedProductIds = currentInput.lockedProductIds.includes(productId)
        ? currentInput.lockedProductIds.filter((id) => id !== productId)
        : [...currentInput.lockedProductIds, productId];
      return { ...currentInput, lockedProductIds };
    });
  }

  async function handleSavePlan(plan: FurnishingPlan, copyLink: boolean) {
    let savedPlan: SavedPlanResponse;
    try {
      // Sprint 10.61: the plan joins the active space (e.g. "Moj dom") so the user's rooms group together.
      savedPlan = await savePlan(plan, input, activeSpace);
    } catch (apiError) {
      // Sprint 10.68: Free saved-plan limit reached → 402 → show the Plus upsell instead of a generic error.
      const status = (apiError as { status?: number } | null)?.status;
      setNotice(status === 402 ? t('plus.saveLimitUpsell') : t('planner.errorUnavailable'));
      return '';
    }
    const url = `${window.location.origin}/plan/${savedPlan.id}`;
    setSavedPlans((currentPlans) => [savedPlan, ...currentPlans.filter((currentPlan) => currentPlan.id !== savedPlan.id)]);

    if (copyLink) {
      await navigator.clipboard.writeText(url);
      setNotice(t('planner.noticeLinkCopied'));
    } else {
      window.history.replaceState({}, '', `/plan/${savedPlan.id}`);
      setNotice(t('spaces.savedTo', { name: activeSpace }));
    }

    return url;
  }

  // Sprint 10.61: "keep designing" a space — make it active, nudge the user to the form for the next room.
  function handleContinueSpace(spaceName: string) {
    setActiveSpace(spaceName);
    setNotice(t('spaces.nowDesigning', { name: spaceName }));
    document.getElementById('planner')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function handleProductClick(planId: string, product: Product) {
    trackProductClick(planId, product);
  }

  async function handleFeedback(planId: string, feedback: PlanFeedback) {
    await sendPlanFeedback(planId, feedback);
    setNotice(t('planner.noticeFeedback'));
  }

  function openSavedPlan(savedPlan: SavedPlanResponse) {
    setInput(savedPlan.input);
    setPlans([savedPlan.plan]);
    setSecondHand([]);
    if (savedPlan.spaceName) setActiveSpace(savedPlan.spaceName);
    setPartialNotice(null);
    setDesign(null);
    setAnalysis(null);
    window.history.replaceState({}, '', `/plan/${savedPlan.id}`);
    setNotice(t('planner.noticeOpened'));
  }

  async function toggleSavedFavorite(savedPlan: SavedPlanResponse) {
    try {
      const updatedPlan = await setSavedPlanFavorite(savedPlan.id, !savedPlan.favorite);
      setSavedPlans((currentPlans) => currentPlans.map((plan) => (plan.id === updatedPlan.id ? updatedPlan : plan)));
    } catch {
      setNotice(t('planner.errorFavorite'));
    }
  }

  async function handleQuickAction(action: QuickPlanAction, plan?: FurnishingPlan) {
    let nextInput: PlannerInput = { ...input };

    if (action === 'cheaper') {
      nextInput = {
        ...nextInput,
        optimizationGoal: 'lowest-price' as OptimizationGoal,
        furnishingLevel: 'basic',
        prompt: `${nextInput.prompt}\n\nSloži jeftiniju verziju i čuvaj budžet.`
      };
    }

    if (action === 'nicer') {
      nextInput = {
        ...nextInput,
        optimizationGoal: 'style-match' as OptimizationGoal,
        furnishingLevel: 'complete',
        prompt: `${nextInput.prompt}\n\nSloži ljepšu i skladniju verziju.`
      };
    }

    if (action === 'least-stores') {
      nextInput = {
        ...nextInput,
        optimizationGoal: 'least-stores' as OptimizationGoal,
        prompt: `${nextInput.prompt}\n\nSmanji broj trgovina i dostava.`
      };
    }

    if (action === 'single-store') {
      const retailer = mostUsedRetailer(plan) ?? nextInput.selectedRetailers[0] ?? 'IKEA';
      nextInput = {
        ...nextInput,
        retailerMode: 'single',
        selectedRetailers: [retailer],
        optimizationGoal: 'least-stores' as OptimizationGoal,
        prompt: `${nextInput.prompt}\n\nSloži sve iz jedne trgovine: ${retailer}.`
      };
    }

    setInput(nextInput);
    await runGeneration(nextInput);
  }

  // Sprint 10.74 (C): the AI ran but wasn't sure what was asked (garbage / off-topic / very-vague typed prompt).
  // We still show a plan (never block the funnel), but nudge the user to describe a room + budget rather than
  // silently presenting a guessed room as if it were exactly what they asked for.
  const lowConfidence = !!analysis && analysis.aiUsed === true && submittedPrompt.length > 0
    && (analysis.confidence ?? 1) < 0.4;

  return (
    <section className="planner-section shell" id="planner">
      <div className="section-heading left planner-heading-row">
        <div>
          <span className="eyebrow">{t('planner.eyebrow')}</span>
          <h2>{t('planner.heading')}</h2>
          <p>{t('planner.subheading')}</p>
        </div>
        <div className="demo-status">
          <span>{t('planner.plansBuilt')}</span>
          <strong>{generationCount}</strong>
        </div>
      </div>

      {marketNote && <div className="planner-notice market-note">{marketNote}</div>}

      {!config.available && <div className="planner-notice market-note">{t('planner.marketComingSoon')}</div>}

      {notice && <div className="planner-notice">{notice}</div>}

      {/* Sprint 10.63: real account state. Signed in → plans are tied to the account; guest → saved in this
          browser, with a one-click way back to the sign-in front door. */}
      <div className="account-strip">
        <div className="account-strip-text">
          <span>{t('account.title')}</span>
          <small>{user ? t('account.signedInHint') : t('account.hint')}</small>
        </div>
        {user ? (
          <span className="account-strip-badge">{t('auth.signedInAs', { name: user.name || user.email || '' })}</span>
        ) : (
          <button type="button" className="account-signin-link" onClick={openSignIn}>{t('auth.signIn')}</button>
        )}
      </div>

      <SavedPlansInbox
        plans={savedPlans}
        search={savedSearch}
        onSearchChange={setSavedSearch}
        onOpen={openSavedPlan}
        onFavorite={toggleSavedFavorite}
        activeSpace={activeSpace}
        onActiveSpaceChange={setActiveSpace}
        onContinueSpace={handleContinueSpace}
        defaultSpaceName={t('spaces.defaultName')}
      />

      <div className="planner-layout">
        <div className="planner-panel">
          <PlannerForm input={input} onChange={setInput} onGenerate={handleGenerate} isLoading={isLoading} />
        </div>
        <PlanResults
          plans={plans}
          input={input}
          secondHandSuggestions={secondHand}
          onReplace={handleReplace}
          onToggleLock={handleToggleLock}
          lockedProductIds={input.lockedProductIds}
          onQuickAction={handleQuickAction}
          onSavePlan={handleSavePlan}
          onProductClick={handleProductClick}
          onFeedback={handleFeedback}
          isLoading={isLoading}
          error={error}
          partialNotice={partialNotice}
        />
      </div>

      {lowConfidence && plans.length > 0 && (
        <p className="planner-lowconf-nudge" role="note">{t('planner.lowConfidenceNudge')}</p>
      )}

      {!lowConfidence && analysis && plans.length > 0 && (analysis.userGoalSummary || (analysis.missingImportantInfo?.length ?? 0) > 0) && (
        <section className="ai-insight-card" aria-label={t('planner.aiInsightAria')}>
          <div className="ai-insight-head">
            <span>{analysis.aiUsed ? t('planner.aiUnderstood') : t('planner.weUnderstood')}</span>
          </div>
          {analysis.userGoalSummary && <p className="ai-insight-summary">{analysis.userGoalSummary}</p>}
          {(analysis.missingImportantInfo?.length ?? 0) > 0 && (
            <p className="ai-insight-unsure">
              {t('planner.unsure', { list: analysis.missingImportantInfo!.join(', ') })}
            </p>
          )}
        </section>
      )}

      {design && plans.length > 0 && (
        <section className="design-assistant-card" aria-label={t('planner.designAria')}>
          <div className="design-assistant-head">
            <span>{t('planner.designTitle')}</span>
            <small>{t('planner.designHint')}</small>
          </div>
          <p className="design-assistant-summary">{design.summary}</p>
          {design.highlights.length > 0 && (
            <ul className="design-assistant-highlights">
              {design.highlights.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          )}
        </section>
      )}
    </section>
  );
}
