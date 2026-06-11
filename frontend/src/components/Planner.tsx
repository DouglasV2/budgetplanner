import { useState } from 'react';
import { generatePlan, replaceProduct } from '../api/client';
import type { FurnishingPlan, OptimizationGoal, PlannerInput, Retailer } from '../types';
import { PlannerForm } from './PlannerForm';
import { PlanResults, type QuickPlanAction } from './PlanResults';

const initialInput: PlannerInput = {
  prompt:
    'Imam 1500 € za dnevni boravak od 20 m² u Zagrebu. Želim skandinavski stil, kombiniraj IKEA i JYSK ako ima smisla. Trebam kauč, TV komodu, klub stolić, tepih i lampu.',
  budget: 1500,
  roomType: 'living-room',
  style: 'scandinavian',
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

export function Planner() {
  const [input, setInput] = useState<PlannerInput>(initialInput);
  const [plans, setPlans] = useState<FurnishingPlan[]>([]);
  const [generationCount, setGenerationCount] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function runGeneration(nextInput: PlannerInput) {
    setIsLoading(true);
    setError(null);

    try {
      const response = await generatePlan(nextInput);
      setInput({ ...response.input, lockedProductIds: response.input.lockedProductIds ?? nextInput.lockedProductIds ?? [] });
      setPlans(response.plans);
      setGenerationCount((count) => count + 1);
    } catch (apiError) {
      setError(
        apiError instanceof Error
          ? apiError.message
          : 'Backend API nije dostupan. Pokreni Spring Boot backend na http://localhost:8080.'
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
      setError(apiError instanceof Error ? apiError.message : 'Nisam mogao zamijeniti proizvod preko backend API-ja.');
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

  async function handleQuickAction(action: QuickPlanAction, plan?: FurnishingPlan) {
    let nextInput: PlannerInput = { ...input };

    if (action === 'cheaper') {
      nextInput = {
        ...nextInput,
        optimizationGoal: 'lowest-price' as OptimizationGoal,
        prompt: `${nextInput.prompt}\n\nNapravi jeftiniju verziju i čuvaj budžet.`
      };
    }

    if (action === 'nicer') {
      nextInput = {
        ...nextInput,
        optimizationGoal: 'style-match' as OptimizationGoal,
        prompt: `${nextInput.prompt}\n\nUčini plan estetski ljepšim i skladnijim.`
      };
    }

    if (action === 'least-stores') {
      nextInput = {
        ...nextInput,
        optimizationGoal: 'least-stores' as OptimizationGoal,
        prompt: `${nextInput.prompt}\n\nSmanji broj trgovina i logistiku.`
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
          <span className="eyebrow">UX Sprint 2</span>
          <h2>Prompt-first planner: napiši cilj, zatim fino podešavaj rezultat.</h2>
          <p>
            Forma više nije glavni proizvod. Korisnik prvo napiše što želi, a kontrole služe za korekcije: jeftinije, ljepše, jedna trgovina ili zaključani proizvodi.
          </p>
        </div>
        <div className="demo-status">
          <span>Generated</span>
          <strong>{generationCount}×</strong>
        </div>
      </div>

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
          isLoading={isLoading}
          error={error}
        />
      </div>
    </section>
  );
}
