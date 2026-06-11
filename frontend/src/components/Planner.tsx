import { useEffect, useState } from 'react';
import { generatePlan, getSavedPlan, replaceProduct, savePlan, sendPlanFeedback, trackProductClick } from '../api/client';
import type { FurnishingPlan, OptimizationGoal, PlanFeedback, PlannerInput, Product, Retailer } from '../types';
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
  selectedRetailers: ['IKEA', 'JYSK', 'Pevex'],
  optimizationGoal: 'best-value',
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

export function Planner() {
  const [input, setInput] = useState<PlannerInput>(initialInput);
  const [plans, setPlans] = useState<FurnishingPlan[]>([]);
  const [generationCount, setGenerationCount] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

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

    try {
      const response = await generatePlan(nextInput);
      setInput({ ...response.input, lockedProductIds: response.input.lockedProductIds ?? nextInput.lockedProductIds ?? [] });
      setPlans(response.plans);
      setGenerationCount((count) => count + 1);
    } catch (apiError) {
      setError(
        apiError instanceof Error
          ? apiError.message
          : 'Server aplikacija nije dostupna. Pokreni Spring Boot aplikaciju na http://localhost:8080.'
      );
    } finally {
      setIsLoading(false);
    }
  }

  async function handleGenerate() {
    await runGeneration(input);
  }

  async function handleReplace(planId: string, productId: string) {
    const plan = plans.find((currentPlan) => currentPlan.id === planId);
    if (!plan) return;

    try {
      const updatedPlan = await replaceProduct(plan, input, productId);
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

    if (copyLink) {
      await navigator.clipboard.writeText(url);
      setNotice('Link za plan je kopiran. Možeš ga poslati partneru, frendu ili sebi za kasnije.');
    } else {
      window.history.replaceState({}, '', `/plan/${savedPlan.id}`);
      setNotice('Plan je spremljen. Link u adresnoj traci sada otvara baš ovaj plan.');
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

  async function handleQuickAction(action: QuickPlanAction, plan?: FurnishingPlan) {
    let nextInput: PlannerInput = { ...input };

    if (action === 'cheaper') {
      nextInput = {
        ...nextInput,
        optimizationGoal: 'lowest-price' as OptimizationGoal,
        prompt: `${nextInput.prompt}\n\nSloži jeftiniju verziju i čuvaj budžet.`
      };
    }

    if (action === 'nicer') {
      nextInput = {
        ...nextInput,
        optimizationGoal: 'style-match' as OptimizationGoal,
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
        />
      </div>
    </section>
  );
}
