import { useEffect, useMemo, useRef, useState } from 'react';
import { generatePlan, generatePlanFast, getSavedPlan, listSavedPlans, replaceProduct, savePlan, sendPlanFeedback, setSavedPlanFavorite, startCheckout, trackProductClick } from '../api/client';
import type { FurnishingPlan, OptimizationGoal, PlanFeedback, PlannerInput, PlannerIntentAnalysis, Product, ReplacementChoice, Retailer, RoomType, SavedPlanResponse } from '../types';
import { formatCurrency, retailersForMarket, roomLabels, styleLabels } from '../utils/planner';
import { detectOutOfScope } from '../utils/outOfScope';
import { detectDimensionConstraint } from '../utils/dimensions';
import { detectMultiRoom } from '../utils/multiRoom';
import type { PlanGenerationResponse } from '../api/client';
import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';
import { detectMarketFromText, marketConfig } from '../markets';
import { PlannerForm } from './PlannerForm';
import { PlanResults, type QuickPlanAction } from './PlanResults';
import { MoveInPlanner } from './MoveInPlanner';

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

// Sprint 10.138: remember the user's last typed prompt + budget across reloads (single-room form). Stores only
// the prompt text + budget number — no PII. We persist only an EDITED prompt (not the seeded example), so a
// fresh visitor still gets the localised example and locale switching can still re-seed it.
const PLANNER_DRAFT_KEY = 'budgetspace.plannerDraft';
function readPlannerDraft(): { prompt?: string; budget?: number } | null {
  try {
    const raw = localStorage.getItem(PLANNER_DRAFT_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
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

// Sprint 10.166: whole days since an ISO timestamp — for the "prices captured N days ago" note on an opened plan.
function daysSince(iso: string) {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return 0;
  return Math.max(0, Math.floor((Date.now() - then) / 86_400_000));
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
                    <small>{t('saved.metaLine', { price: formatCurrency(savedPlan.plan.total, savedPlan.input.market), count: savedPlan.plan.items.length, stores })}</small>
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
  const { market, config, setMarket, t, lang } = useLocale();
  // Sprint 10.63: real auth. When the signed-in user changes (sign-in/out), the saved-plans inbox refetches so
  // the now account-owned plans (migrated from the guest session on first sign-in) appear.
  const { user, openSignIn, billingEnabled, aiEnabled, betaMode } = useAuth();
  const isPlus = user?.plan === 'PLUS' || user?.plan === 'PRO';
  // The example prompt is localised: Croatian for HR, English for the other markets. We seed it once from
  // the active market's language (the user can then edit freely).
  const exampleSeed = t('planner.examplePrompt');
  // Sprint 10.138: hydrate the last EDITED prompt + budget from localStorage (a returning user picks up where
  // they left off). A fresh user (no draft) still gets the localised example.
  const [input, setInput] = useState<PlannerInput>(() => {
    const draft = readPlannerDraft();
    return {
      ...initialInput,
      // Sprint 10.156: ignore a stored draft that STARTS with a blank line — that's the signature of the old
      // quick-action leak ("\n\n<suffix>"); a real typed prompt never begins with an empty line. This also
      // cleans up any draft already poisoned before the fix. Falls back to the localised example.
      prompt: draft?.prompt && !/^\s*\n/.test(draft.prompt) ? draft.prompt : exampleSeed,
      budget: draft?.budget && draft.budget > 0 ? draft.budget : initialInput.budget,
      market
    };
  });
  // The example prompt is seeded from t() at mount, but a non-HR market's translations are lazy-loaded, so the
  // first seed can be the English fallback. Re-seed it once the language overlay arrives (t() identity changes
  // on langReady) — but only while the textarea still holds the seeded example, so a user's edits are never lost.
  // The ref tracks the SEEDED EXAMPLE (not a restored draft), so a restored custom prompt is never re-seeded away.
  const seededPromptRef = useRef<string>(exampleSeed);
  const [plans, setPlans] = useState<FurnishingPlan[]>([]);
  const [savedPlans, setSavedPlans] = useState<SavedPlanResponse[]>([]);
  const [savedSearch, setSavedSearch] = useState('');
  const [generationCount, setGenerationCount] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  // Sprint 10.168: monotonic token so a slow AI result from an OLDER generation can't clobber a newer one
  // (the "plan reset to the previous list" race). Only the latest runGeneration is allowed to apply results.
  const genRef = useRef(0);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  // Sprint 10.88: the actionable Plus upsell card, shown when a save hits the Free 3-plan cap (402).
  const [saveLimitHit, setSaveLimitHit] = useState(false);
  const [upgradeBusy, setUpgradeBusy] = useState(false);
  // Sprint 10.89: the "Plus = more AI" nudge, shown when AI is on but this plan fell back to rule-based for a
  // non-Plus owner (i.e. their tier's AI allowance is spent). Dismissible for the session.
  const [aiNudgeDismissed, setAiNudgeDismissed] = useState(false);
  const [partialNotice, setPartialNotice] = useState<string | null>(null);
  const [analysis, setAnalysis] = useState<PlannerIntentAnalysis | null>(null);
  // Sprint 10.74 (C): the prompt the user actually typed, captured at submit (response.input.prompt is cleared on
  // the AI path). Used to show a gentle "I wasn't sure — describe a room + budget" nudge on low-confidence input.
  const [submittedPrompt, setSubmittedPrompt] = useState('');
  // Sprint 10.78: true while the instant rule-based draft is shown and the AI refine is still in flight.
  const [refining, setRefining] = useState(false);
  // Sprint 10.51: the separate "Rabljeno" (second-hand) suggestions — kept entirely out of every plan total.
  const [secondHand, setSecondHand] = useState<Product[]>([]);
  // Sprint 10.61: the active "space" (home) that new room-plans save into; default "Moj dom".
  const [activeSpace, setActiveSpace] = useState<string>(() => t('spaces.defaultName'));
  // Sprint 10.13 (#3): reversible "we picked your country from the prompt" note.
  const [marketNote, setMarketNote] = useState<string | null>(null);
  // Sprint 10.109: planner scope. 'single' = the existing one-room flow (default, unchanged); 'apartment' =
  // the Move-In multi-room mode. Purely additive — the single-room path below is untouched when scope='single'.
  const [scope, setScope] = useState<'single' | 'apartment'>('single');
  // Sprint 10.116: when a free-text prompt names several rooms, this seeds the Move-In mode the nudge switches to.
  const [moveInSeed, setMoveInSeed] = useState<{ rooms: RoomType[]; budget: number } | null>(null);
  // Sprint 10.166: when the shown plan is an OPENED SAVED plan, remember when it was saved so we can surface a
  // "prices captured N days ago — refresh" note. Null for a freshly generated plan (its prices are current).
  const [openedSavedAt, setOpenedSavedAt] = useState<string | null>(null);

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
      // Sprint 10.137: when the country changes, default to ALL of the new market's stores (incl. its local
      // retailers), not just the overlap with the previous market's picks. The old intersection collapsed to
      // just IKEA for non-HR markets (e.g. ES, where Kenay/Banak/Merkamueble were dropped), so local depth never
      // surfaced and plans under-filled. Single-store mode keeps a single store (mapped into the new market).
      const allowed = retailersForMarket(market);
      const nextRetailers = current.retailerMode === 'single'
        ? [current.selectedRetailers.find((retailer) => allowed.includes(retailer)) ?? allowed[0] ?? 'IKEA']
        : allowed;
      return { ...current, market, selectedRetailers: nextRetailers };
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

  // Sprint 10.138: persist the prompt + budget — but only an EDITED prompt (≠ the seeded example), so a fresh
  // visitor still gets the localised example and locale re-seeding keeps working. Restored on next mount above.
  useEffect(() => {
    try {
      if (input.prompt && input.prompt !== seededPromptRef.current) {
        localStorage.setItem(PLANNER_DRAFT_KEY, JSON.stringify({ prompt: input.prompt, budget: input.budget }));
      }
    } catch { /* ignore quota / private mode */ }
  }, [input.prompt, input.budget]);

  useEffect(() => {
    const sharedPlanId = readSharedPlanIdFromUrl();
    if (!sharedPlanId) return;

    setIsLoading(true);
    getSavedPlan(sharedPlanId)
      .then((savedPlan) => {
        setInput(savedPlan.input);
        setPlans([savedPlan.plan]);
        setSecondHand([]);
        setOpenedSavedAt(savedPlan.createdAt);
        // Sprint 10.167: view a shared plan in ITS OWN market — a UK plan shows £ (not the viewer's €) and its
        // labels in that market's language, coherently. Aligning the whole LocaleContext (not just a currency
        // global) also makes this a definitive market choice, so a late geo-country resolve can't clobber it back.
        if (savedPlan.input.market) setMarket(savedPlan.input.market);
        setNotice(t('planner.noticeLoaded'));
      })
      .catch(() => setError(t('planner.errorNotFound')))
      .finally(() => setIsLoading(false));
  }, []);

  async function runGeneration(nextInput: PlannerInput) {
    const myGen = ++genRef.current; // this generation's token; another runGeneration supersedes it
    // Sprint 10.13 (#3): if the prompt names a country/city we support, auto-switch the market for
    // this plan and show a reversible note. The selector stays the source of truth (user can revert).
    let effectiveInput = nextInput;
    const detected = detectMarketFromText(nextInput.prompt);
    if (detected && detected !== market) {
      const cfg = marketConfig(detected);
      setMarket(detected);
      setMarketNote(
        lang === 'hr'
          ? `Tržište postavljeno na ${cfg.label} (iz tvog opisa). Promijeni gore ako nije točno.`
          : `Market set to ${cfg.label} (from your description). Change it above if needed.`
      );
      effectiveInput = { ...nextInput, market: detected };
    } else {
      setMarketNote(null);
    }

    // Sprint 10.102: capture the typed prompt up front (the AI response clears input.prompt) so the kitchen
    // scope note can tell a fitted-kitchen request from a normal one on the instant draft, not only after AI.
    setSubmittedPrompt((effectiveInput.prompt ?? '').trim());

    setIsLoading(true);
    setError(null);
    setNotice(null);
    setPartialNotice(null);
    setAnalysis(null);
    setSecondHand([]);
    setRefining(false);
    setOpenedSavedAt(null); // a fresh generation prices against the current catalog → drop the "saved plan" note

    // Sprint 10.78: two-phase generate. Paint an INSTANT deterministic draft (~50ms) so the user isn't staring
    // at a ~2s spinner, then refine it with the AI result (which we kick off immediately, in parallel).
    const aiPromise = generatePlan(effectiveInput);
    let shown = false;     // has any plan been rendered?
    let aiLanded = false;  // did the AI result already replace the draft?

    try {
      const draft = await generatePlanFast(effectiveInput);
      if (myGen === genRef.current && !aiLanded) {
        applyResponse(draft, effectiveInput, false);
        shown = true;
        setRefining(true);    // plan is visible; AI is sharpening it
        setIsLoading(false);
      }
    } catch {
      // Fast path failed — the AI result (or its error) drives the UI below.
    }

    try {
      const ai = await aiPromise;
      if (myGen !== genRef.current) return; // a newer generation started → drop this stale result
      aiLanded = true;
      applyResponse(ai, effectiveInput, true);
      shown = true;
    } catch {
      if (myGen === genRef.current && !shown) {
        // Sprint 10.167: always show the localised, friendly error — never a raw thrown/backend string (which
        // was hardcoded Croatian and leaked the word "Backend" to users, incl. in English).
        setError(t('planner.errorUnavailable'));
      }
      // else: AI failed but the deterministic draft is already shown — keep it (graceful).
    } finally {
      // Only the latest generation owns the loading/refining state; a superseded one must not clear it.
      if (myGen === genRef.current) {
        setRefining(false);
        setIsLoading(false);
      }
    }
  }

  // Sprint 10.78: render a plan response. isFinal=true only for the AI (authoritative) result — only then do we
  // count the generation, fetch the AI design summary, and capture the typed prompt for the low-confidence nudge.
  function applyResponse(response: PlanGenerationResponse, effectiveInput: PlannerInput, isFinal: boolean) {
    // Sprint 10.168: keep the user's typed prompt in the textarea. The AI response returns input.prompt=''
    // (the server analysis is authoritative), but blanking the box loses what the user wrote and they can't
    // review/tweak/re-run their own request. submittedPrompt is captured separately, so nothing depends on this.
    setInput({ ...response.input, prompt: effectiveInput.prompt, market: response.input.market ?? effectiveInput.market, lockedProductIds: response.input.lockedProductIds ?? effectiveInput.lockedProductIds ?? [] });
    setPlans(response.plans);
    setAnalysis(response.intentAnalysis ?? null);
    setSecondHand(response.secondHandSuggestions ?? []);
    const hasAnyItems = response.plans.some((plan) => plan.items.length > 0);
    // Sprint 10.124: use the LOCALIZED generic notice — the backend catalogWarning is Croatian-only and lists
    // the room's core categories (often not what a focused user asked for), so it leaked Croatian + wrong info.
    setPartialNotice(!hasAnyItems
      ? t('planner.partialNone')
      : (response.partialPlan ? t('planner.partialBest') : null));
    if (isFinal) {
      setSubmittedPrompt((effectiveInput.prompt ?? '').trim());
      setGenerationCount((count) => count + 1);
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
    } catch {
      // Localised error only — never surface a raw thrown/backend string to the user.
      setError(t('planner.errorReplace'));
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
      // Sprint 10.68/10.88: Free saved-plan limit reached → 402 → show the actionable Plus upsell card; any other
      // error is a generic notice.
      const status = (apiError as { status?: number } | null)?.status;
      if (status === 402) {
        setSaveLimitHit(true);
      } else {
        setNotice(t('planner.errorUnavailable'));
      }
      return '';
    }
    const url = `${window.location.origin}/plan/${savedPlan.id}`;
    setSaveLimitHit(false); // a save went through → clear any lingering upsell
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

  // Sprint 10.88: from the save-limit upsell, start a real Stripe checkout (signed-in Plus-eligible user).
  async function startUpgrade() {
    setUpgradeBusy(true);
    try {
      const { url } = await startCheckout();
      window.location.href = url; // Stripe's hosted checkout
    } catch {
      setUpgradeBusy(false);
      setNotice(t('pricing.checkoutError'));
    }
  }

  // For guests / when billing is off: take them to the pricing section (sign-in for Plus, or the waitlist).
  function goToPricing() {
    setSaveLimitHit(false);
    setAiNudgeDismissed(true);
    document.getElementById('pricing')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  // Sprint 10.88/10.89: the upgrade CTA shared by the save-limit and AI-allowance upsells. Signed-in + billing on
  // → real Stripe checkout; guest + billing on → sign in (an account is needed to subscribe); billing off →
  // the pricing section (sign-in / waitlist).
  function upsellCta() {
    if (billingEnabled && user) {
      return (
        <button type="button" className="planner-upsell-cta" onClick={() => void startUpgrade()} disabled={upgradeBusy}>
          {upgradeBusy ? t('pricing.redirecting') : t('pricing.upgradeCta')}
        </button>
      );
    }
    if (billingEnabled) {
      return <button type="button" className="planner-upsell-cta" onClick={openSignIn}>{t('pricing.signInForPlus')}</button>;
    }
    return <button type="button" className="planner-upsell-cta" onClick={goToPricing}>{t('plus.seePricing')}</button>;
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
    setOpenedSavedAt(savedPlan.createdAt);
    // Sprint 10.167: a saved plan opened from another market renders in ITS market — currency AND labels align to
    // the plan (coherent, not a currency-only global that mixed languages). setMarket is a definitive choice, so a
    // late geo resolve can't clobber it.
    if (savedPlan.input.market) setMarket(savedPlan.input.market);
    if (savedPlan.spaceName) setActiveSpace(savedPlan.spaceName);
    setPartialNotice(null);
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
    // The quick action changes the OPTIONS (goal/level/stores); the SUFFIX is a request-only nudge to the AI.
    let nextInput: PlannerInput = { ...input };
    let suffix = '';

    if (action === 'cheaper') {
      nextInput = { ...nextInput, optimizationGoal: 'lowest-price' as OptimizationGoal, furnishingLevel: 'basic' };
      suffix = t('results.quickCheaperSuffix');
    }
    if (action === 'nicer') {
      nextInput = { ...nextInput, optimizationGoal: 'style-match' as OptimizationGoal, furnishingLevel: 'complete' };
      suffix = t('results.quickNicerSuffix');
    }
    if (action === 'least-stores') {
      nextInput = { ...nextInput, optimizationGoal: 'least-stores' as OptimizationGoal };
      suffix = 'Smanji broj trgovina i dostava.';
    }
    if (action === 'single-store') {
      const retailer = mostUsedRetailer(plan) ?? nextInput.selectedRetailers[0] ?? 'IKEA';
      nextInput = { ...nextInput, retailerMode: 'single', selectedRetailers: [retailer], optimizationGoal: 'least-stores' as OptimizationGoal };
      suffix = t('results.quickSingleStoreSuffix', { retailer });
    }

    // Sprint 10.156: the SUFFIX must NOT enter input.prompt (state). The AI response clears input.prompt after a
    // generate, so appending the suffix there left the textarea holding just "\n\n<suffix>", which Sprint 10.138
    // then PERSISTED as the user's draft — so "Složi ljepšu i skladniju verziju." reappeared in the box on the
    // next load. Keep state's prompt untouched; append the suffix ONLY to the request, based on the originally
    // typed prompt (submittedPrompt) so the regeneration keeps the room/budget context.
    const base = (input.prompt && input.prompt.trim()) ? input.prompt.trim() : submittedPrompt;
    setInput(nextInput);
    await runGeneration({ ...nextInput, prompt: `${base}\n\n${suffix}`.trim() });
  }

  // Sprint 10.74 (C): the AI ran but wasn't sure what was asked (garbage / off-topic / very-vague typed prompt).
  // We still show a plan (never block the funnel), but nudge the user to describe a room + budget rather than
  // silently presenting a guessed room as if it were exactly what they asked for.
  const lowConfidence = !!analysis && analysis.aiUsed === true && submittedPrompt.length > 0
    && (analysis.confidence ?? 1) < 0.4;

  // Sprint 10.115: honest out-of-scope banner — when the typed prompt clearly asks for things we don't sell
  // (electronics / appliances / building materials), say so plainly. Deterministic + localized (not AI text).
  // Sprint 10.155: do NOT gate this on plans.length. A PURE out-of-scope request (e.g. "washing machine") now
  // correctly yields an EMPTY plan, and the honest "we don't sell {what}" message is exactly what the user needs
  // THEN — the old `plans.length > 0` gate suppressed the banner for the most important case (15-market sweep:
  // appliance prompts returned 0 items in 12/15 markets, so the user saw a generic "no plan" empty state instead).
  const outOfScope = detectOutOfScope(submittedPrompt);
  const dimensionConstraint = plans.length > 0 && detectDimensionConstraint(submittedPrompt);
  const outOfScopeWhatKey = outOfScope === 'electronics'
    ? 'results.outOfScopeElectronics'
    : outOfScope === 'appliances'
    ? 'results.outOfScopeAppliances'
    : outOfScope === 'outdoor'
    ? 'results.outOfScopeOutdoor'
    : 'results.outOfScopeMaterials';

  // Sprint 10.116: the prompt named several rooms — the single-room plan can't do that; nudge to Cijeli stan.
  const multiRoom = plans.length > 0 ? detectMultiRoom(submittedPrompt) : null;

  return (
    <section className="planner-section shell" id="planner">
      {/* Sprint 10.144: dropped the floating "Složeno planova / 0" stat card — an empty "0" stat reads as a
          generic AI-dashboard mockup (founder flagged the hero as AI-evski). The hero is now a clean, confident
          left-aligned editorial header at a comfortable measure. */}
      <div className="section-heading left planner-heading-row">
        <div>
          <span className="eyebrow">{t('planner.eyebrow')}</span>
          {/* Sprint 10.156: give the headline brand personality (less flat/generic) — the "payoff" second
              sentence renders in the brand clay. Robust across locales: if there's no sentence break we just
              show the plain heading. */}
          <h2>{(() => {
            const heading = t('planner.heading');
            const at = heading.indexOf('. ');
            return at < 0
              ? heading
              : <>{heading.slice(0, at + 1)} <span className="hero-accent">{heading.slice(at + 2)}</span></>;
          })()}</h2>
          <p>{t('planner.subheading')}</p>
        </div>
      </div>

      {marketNote && <div className="planner-notice market-note">{marketNote}</div>}

      {!config.available && <div className="planner-notice market-note">{t('planner.marketComingSoon')}</div>}

      {notice && <div className="planner-notice">{notice}</div>}
      {/* Sprint 10.105: no upsell in the free beta. The save cap is raised high (env) so it never fires, and this
          card stays hidden. When one-time payments are wired (betaMode=false) it becomes the Design Session unlock. */}
      {!betaMode && saveLimitHit && (
        <div className="planner-upsell" role="status">
          <p className="planner-upsell-text">{t('plus.saveLimitUpsell')}</p>
          <div className="planner-upsell-actions">{upsellCta()}</div>
        </div>
      )}
      {/* AI-allowance nudge: when AI is on and the daily allowance is spent. In the free beta it's honest "come
          back tomorrow" copy with no price/CTA; when payments are wired (betaMode=false) it becomes the unlock. */}
      {aiEnabled && analysis && !analysis.aiUsed && !isPlus && !aiNudgeDismissed && (
        <div className="planner-upsell" role="status">
          <p className="planner-upsell-text">{t(betaMode ? 'plus.aiCapBeta' : 'plus.aiUpsell')}</p>
          <div className="planner-upsell-actions">
            {!betaMode && upsellCta()}
            <button type="button" className="planner-upsell-dismiss" onClick={() => setAiNudgeDismissed(true)}>{t('plus.dismiss')}</button>
          </div>
        </div>
      )}

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

      {/* Sprint 10.109: scope switch — one room (default) vs the whole apartment. Same prompt/engine; the
          apartment branch is a self-contained orchestration over the single-room generator. */}
      <div className="scope-toggle" role="group" aria-label={t('moveIn.scopeAria')}>
        <button type="button" className={scope === 'single' ? 'scope-option active' : 'scope-option'} aria-pressed={scope === 'single'} onClick={() => setScope('single')}>{t('moveIn.scopeSingle')}</button>
        <button type="button" className={scope === 'apartment' ? 'scope-option active' : 'scope-option'} aria-pressed={scope === 'apartment'} onClick={() => setScope('apartment')}>{t('moveIn.scopeApartment')}</button>
      </div>

      {/* Both panes stay MOUNTED; we toggle visibility so switching scope never loses your work
          (the apartment results / a generated single-room plan persist across the toggle). */}
      <div hidden={scope !== 'single'}>
      {outOfScope && (
        <div className="out-of-scope-banner" role="note">
          <span className="out-of-scope-mark" aria-hidden="true">i</span>
          <span>{t('results.outOfScopeNotice', { what: t(outOfScopeWhatKey) })}</span>
        </div>
      )}
      {dimensionConstraint && (
        <div className="out-of-scope-banner" role="note">
          <span className="out-of-scope-mark" aria-hidden="true">i</span>
          <span>{t('results.dimensionNotice')}</span>
        </div>
      )}
      {multiRoom && (
        <div className="out-of-scope-banner multi-room-banner" role="note">
          <span className="out-of-scope-mark" aria-hidden="true">i</span>
          <span className="multi-room-text">{t('results.multiRoomNotice')}</span>
          <button
            type="button"
            className="multi-room-cta"
            onClick={() => {
              setMoveInSeed({ rooms: multiRoom.rooms, budget: input.budget });
              setScope('apartment');
              document.getElementById('planner')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }}
          >
            {t('results.multiRoomCta')}
          </button>
        </div>
      )}
      {openedSavedAt && plans.length > 0 && (
        <div className="planner-notice saved-freshness-note" role="note">
          <span>{t('results.savedPlanFreshnessNote', { date: formatSavedDate(openedSavedAt), days: daysSince(openedSavedAt) })}</span>
          <button type="button" className="saved-freshness-refresh" onClick={handleGenerate} disabled={isLoading}>
            {t('results.savedPlanRefresh')}
          </button>
        </div>
      )}
      <div className="planner-layout">
        <div className="planner-panel">
          <PlannerForm input={input} onChange={setInput} onGenerate={handleGenerate} isLoading={isLoading} />
          {/* Sprint 10.78: the draft plan is already on the right; the AI is sharpening it in the background. */}
          {refining && plans.length > 0 && (
            <p className="planner-refining" role="status">{t('planner.refining')}</p>
          )}
        </div>
        <PlanResults
          active={scope === 'single'}
          plans={plans}
          input={input}
          submittedPrompt={submittedPrompt}
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
            // Sprint 10.107: a generic refine hint — never echo the AI's raw schema field names
            // (preferredRetailers, qualityPreference, …) to the user.
            <p className="ai-insight-unsure">{t('planner.unsure')}</p>
          )}
          {/* Sprint 10.163 (reliance / UCPD): only when AI actually produced this — the estimates aren't advice. */}
          {analysis.aiUsed && <p className="field-help ai-not-advice-note">{t('planner.aiNotAdvice')}</p>}
        </section>
      )}

      {/* Sprint 10.152: removed the "Design assistant" card — it re-described the plan in a rule-based, backend-
          HR-only summary (so it showed Croatian even on UK/other markets) and duplicated the plan + the "AI
          understood" card above. Confusing + a localization leak; dropped. (A real AI design rationale can return
          later as a proper localized feature.) */}
      </div>

      <div hidden={scope !== 'apartment'}>
        <MoveInPlanner
          active={scope === 'apartment'}
          baseInput={input}
          activeSpace={activeSpace}
          onSavedPlan={(saved) => setSavedPlans((current) => [saved, ...current.filter((plan) => plan.id !== saved.id)])}
          onNotice={setNotice}
          seed={moveInSeed}
        />
      </div>
    </section>
  );
}
