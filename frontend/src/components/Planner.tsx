import { useEffect, useMemo, useState } from 'react';
import { generatePlan, getSavedPlan, listSavedPlans, replaceProduct, savePlan, sendPlanFeedback, setSavedPlanFavorite, trackProductClick } from '../api/client';
import type { FurnishingPlan, OptimizationGoal, PlanFeedback, PlannerInput, Product, ReplacementChoice, Retailer, SavedPlanResponse } from '../types';
import { formatCurrency, roomLabels, styleLabels } from '../utils/planner';
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

function SavedPlansInbox({
  plans,
  search,
  onSearchChange,
  onOpen,
  onFavorite
}: {
  plans: SavedPlanResponse[];
  search: string;
  onSearchChange: (value: string) => void;
  onOpen: (plan: SavedPlanResponse) => void;
  onFavorite: (plan: SavedPlanResponse) => void;
}) {
  const filteredPlans = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return plans.slice(0, 8);
    return plans.filter((plan) => planSearchText(plan).includes(query)).slice(0, 12);
  }, [plans, search]);

  if (!plans.length) return null;

  return (
    <details className="saved-plans-inbox">
      <summary>
        <div>
          <span>Moji planovi</span>
          <strong>{plans.length} spremljeno · {plans.filter((plan) => plan.favorite).length} favorita</strong>
        </div>
        <small>Otvori kad želiš nastaviti kasnije.</small>
      </summary>
      <div className="saved-plans-toolbar">
        <label>
          <span>Pretraži moje planove</span>
          <input value={search} onChange={(event) => onSearchChange(event.target.value)} placeholder="Pretraži moje planove" />
        </label>
      </div>
      <div className="saved-plans-list">
        {filteredPlans.map((savedPlan) => {
          const stores = savedPlan.plan.retailersUsed.join(' + ') || 'trgovine nisu zapisane';
          return (
            <article className={savedPlan.favorite ? 'saved-plan-card favorite' : 'saved-plan-card'} key={savedPlan.id}>
              <div>
                <span>{savedPlan.favorite ? 'Favorit' : 'Spremljeno'} · {formatSavedDate(savedPlan.createdAt)}</span>
                <strong>{roomLabels[savedPlan.input.roomType]}</strong>
                <small>{formatCurrency(savedPlan.plan.total)} · {savedPlan.plan.items.length} proizvoda · {stores}</small>
              </div>
              <div className="saved-plan-actions">
                <button
                  type="button"
                  className={savedPlan.favorite ? 'favorite-toggle active' : 'favorite-toggle'}
                  onClick={() => onFavorite(savedPlan)}
                  aria-label={savedPlan.favorite ? 'Makni iz favorita' : 'Dodaj u favorite'}
                >
                  {savedPlan.favorite ? '★ Favorit' : '☆ Označi'}
                </button>
                <button type="button" onClick={() => onOpen(savedPlan)}>Otvori</button>
              </div>
            </article>
          );
        })}
        {!filteredPlans.length && <p className="saved-empty">Nema spremljenog plana za taj pojam.</p>}
      </div>
    </details>
  );
}

export function Planner() {
  const [input, setInput] = useState<PlannerInput>(initialInput);
  const [plans, setPlans] = useState<FurnishingPlan[]>([]);
  const [savedPlans, setSavedPlans] = useState<SavedPlanResponse[]>([]);
  const [savedSearch, setSavedSearch] = useState('');
  const [generationCount, setGenerationCount] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [partialNotice, setPartialNotice] = useState<string | null>(null);

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
  }, []);

  useEffect(() => {
    const sharedPlanId = readSharedPlanIdFromUrl();
    if (!sharedPlanId) return;

    setIsLoading(true);
    getSavedPlan(sharedPlanId)
      .then((savedPlan) => {
        setInput(savedPlan.input);
        setPlans([savedPlan.plan]);
        setNotice('Učitan je spremljeni plan. Možeš ga kopirati, mijenjati ili složiti novu verziju.');
      })
      .catch(() => setError('Nisam našao spremljeni plan. Možda je link pogrešan ili server nije pokrenut.'))
      .finally(() => setIsLoading(false));
  }, []);

  async function runGeneration(nextInput: PlannerInput) {
    setIsLoading(true);
    setError(null);
    setNotice(null);
    setPartialNotice(null);

    try {
      const response = await generatePlan(nextInput);
      setInput({ ...response.input, lockedProductIds: response.input.lockedProductIds ?? nextInput.lockedProductIds ?? [] });
      setPlans(response.plans);
      const hasAnyItems = response.plans.some((plan) => plan.items.length > 0);
      if (!hasAnyItems) {
        setPartialNotice('Nema dovoljno proizvoda za ovaj zahtjev. Pokušaj povećati budžet ili ukloniti ograničenje trgovine.');
      } else {
        setPartialNotice(response.partialPlan ? (response.catalogWarning ?? 'Nemamo još dovoljno proizvoda za kompletan plan. Ovo je najbolja dostupna kombinacija.') : null);
      }
      setGenerationCount((count) => count + 1);
    } catch (apiError) {
      setError(
        apiError instanceof Error
          ? apiError.message
          : 'Plan trenutno nije dostupan. Probaj ponovno za koju minutu.'
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
      setError(apiError instanceof Error ? apiError.message : 'Nisam uspio zamijeniti proizvod. Probaj ponovno.');
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
    const savedPlan = await savePlan(plan, input);
    const url = `${window.location.origin}/plan/${savedPlan.id}`;
    setSavedPlans((currentPlans) => [savedPlan, ...currentPlans.filter((currentPlan) => currentPlan.id !== savedPlan.id)]);

    if (copyLink) {
      await navigator.clipboard.writeText(url);
      setNotice('Link za plan je kopiran. Plan je spremljen i možeš ga naći u Mojim planovima.');
    } else {
      window.history.replaceState({}, '', `/plan/${savedPlan.id}`);
      setNotice('Plan je spremljen u Moje planove. Možeš ga kasnije pretražiti ili označiti kao favorit.');
    }

    return url;
  }

  function handleProductClick(planId: string, product: Product) {
    trackProductClick(planId, product);
  }

  async function handleFeedback(planId: string, feedback: PlanFeedback) {
    await sendPlanFeedback(planId, feedback);
    setNotice('Hvala — ova reakcija nam govori što treba popraviti u sljedećem planu.');
  }

  function openSavedPlan(savedPlan: SavedPlanResponse) {
    setInput(savedPlan.input);
    setPlans([savedPlan.plan]);
    setPartialNotice(null);
    window.history.replaceState({}, '', `/plan/${savedPlan.id}`);
    setNotice('Otvoren je spremljeni plan. Možeš ga odmah kopirati, prilagoditi ili složiti novu verziju.');
  }

  async function toggleSavedFavorite(savedPlan: SavedPlanResponse) {
    try {
      const updatedPlan = await setSavedPlanFavorite(savedPlan.id, !savedPlan.favorite);
      setSavedPlans((currentPlans) => currentPlans.map((plan) => (plan.id === updatedPlan.id ? updatedPlan : plan)));
    } catch {
      setNotice('Nisam uspio promijeniti favorit. Probaj ponovno za koju minutu.');
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

  return (
    <section className="planner-section shell" id="planner">
      <div className="section-heading left planner-heading-row">
        <div>
          <span className="eyebrow">Planer za kupnju</span>
          <h2>Prvo napišeš želju. Desno dobiješ gotov plan.</h2>
          <p>
            Nema komplicirane forme. Opiši prostor svojim riječima, a zatim po potrebi dotjeraj budžet, trgovine ili stvari koje već imaš.
          </p>
        </div>
        <div className="demo-status">
          <span>Složeno planova</span>
          <strong>{generationCount}</strong>
        </div>
      </div>

      {notice && <div className="planner-notice">{notice}</div>}

      <SavedPlansInbox
        plans={savedPlans}
        search={savedSearch}
        onSearchChange={setSavedSearch}
        onOpen={openSavedPlan}
        onFavorite={toggleSavedFavorite}
      />

      <div className="planner-layout">
        <div className="planner-panel">
          <PlannerForm input={input} onChange={setInput} onGenerate={handleGenerate} isLoading={isLoading} />
        </div>
        <PlanResults
          plans={plans}
          input={input}
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
    </section>
  );
}
