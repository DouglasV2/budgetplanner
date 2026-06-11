import { useEffect, useState, type ReactNode } from 'react';
import type { FurnishingPlan, PlanFeedback, PlannerInput, Product, ProductCategory } from '../types';
import { categoryLabels, formatCurrency, formatPlanForSharing, getRetailerBreakdown, roomLabels, styleLabels } from '../utils/planner';

export type QuickPlanAction = 'cheaper' | 'nicer' | 'single-store' | 'least-stores';

interface PlanResultsProps {
  plans: FurnishingPlan[];
  input: PlannerInput;
  onReplace: (planId: string, productId: string) => void;
  onToggleLock: (productId: string) => void;
  lockedProductIds: string[];
  onQuickAction: (action: QuickPlanAction, plan?: FurnishingPlan) => void;
  onSavePlan: (plan: FurnishingPlan, copyLink: boolean) => Promise<string>;
  onProductClick: (planId: string, product: Product) => void;
  onFeedback: (planId: string, feedback: PlanFeedback) => Promise<void>;
  isLoading?: boolean;
  error?: string | null;
}

const effortLabels = {
  Low: 'Jednostavno',
  Medium: 'Umjereno',
  High: 'Više trgovina'
};

const feedbackOptions: Array<{ value: PlanFeedback; label: string }> = [
  { value: 'useful', label: 'Korisno' },
  { value: 'too-expensive', label: 'Preskupo' },
  { value: 'wrong-style', label: 'Ne sviđa mi se izgled' },
  { value: 'too-many-stores', label: 'Previše trgovina' }
];

function labelCategories(categories: ProductCategory[]) {
  if (!categories.length) return 'nema posebnih oznaka';
  return categories.map((category) => categoryLabels[category]).join(', ');
}

function missingCategories(plan: FurnishingPlan, input: PlannerInput) {
  return input.mustHaveCategories.filter((category) => !plan.items.some((item) => item.product.category === category));
}

function defaultSummary(plan: FurnishingPlan, input: PlannerInput) {
  const room = roomLabels[input.roomType];
  const itemList = plan.items.slice(0, 5).map((item) => categoryLabels[item.product.category].toLowerCase()).join(', ');
  return `Za ${formatCurrency(input.budget)} složili smo ${room}: ${itemList}. Plan koristi ${plan.retailersUsed.length === 1 ? 'jednu trgovinu' : `${plan.retailersUsed.length} trgovine`} i ostaje ${plan.total <= input.budget ? 'unutar budžeta' : 'malo iznad budžeta'}.`;
}

function budgetSentence(plan: FurnishingPlan, input: PlannerInput) {
  const difference = Math.abs(plan.total - input.budget);
  if (plan.total <= input.budget) return `Ostaje ti još ${formatCurrency(difference)} za sitnice, dostavu ili zamjene.`;
  return `Plan prelazi budžet za ${formatCurrency(difference)}, ali pokazuje što dobivaš ako malo rastegneš budžet.`;
}

function UnderstandingSummary({ input }: { input: PlannerInput }) {
  return (
    <div className="understood-card">
      <div className="understood-header">
        <span>Razumjeli smo</span>
        <strong>{formatCurrency(input.budget)} budžet</strong>
      </div>
      <div className="understood-grid">
        <div>
          <span>Prostorija</span>
          <strong>{roomLabels[input.roomType]}</strong>
        </div>
        <div>
          <span>Izgled</span>
          <strong>{styleLabels[input.style]}</strong>
        </div>
        <div>
          <span>Veličina</span>
          <strong>{input.size} m²</strong>
        </div>
        <div>
          <span>Trgovine</span>
          <strong>{input.retailerMode === 'single' ? `samo ${input.selectedRetailers[0]}` : input.selectedRetailers.join(' + ')}</strong>
        </div>
      </div>
      <div className="understood-tags">
        <span>Obavezno: {labelCategories(input.mustHaveCategories)}</span>
        <span>Već imaš: {labelCategories(input.alreadyHaveCategories)}</span>
        {input.lockedProductIds.length > 0 && <span>Zadržavaš: {input.lockedProductIds.length} proizvoda</span>}
      </div>
    </div>
  );
}

function ResultShell({ children }: { children: ReactNode }) {
  return (
    <div className="results-shell">
      <div className="results-title-bar">
        <div>
          <span className="step-kicker">Rezultat</span>
          <h3>Tvoj plan za kupnju</h3>
        </div>
        <small>Ovdje vidiš gotov popis proizvoda, ukupnu cijenu i zašto smo ih odabrali.</small>
      </div>
      {children}
    </div>
  );
}

export function PlanResults({
  plans,
  input,
  onReplace,
  onToggleLock,
  lockedProductIds,
  onQuickAction,
  onSavePlan,
  onProductClick,
  onFeedback,
  isLoading = false,
  error = null
}: PlanResultsProps) {
  const [copiedPlanId, setCopiedPlanId] = useState<string | null>(null);
  const [savingPlanId, setSavingPlanId] = useState<string | null>(null);
  const [feedbackByPlan, setFeedbackByPlan] = useState<Record<string, PlanFeedback>>({});
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);

  useEffect(() => {
    if (!plans.length) {
      setSelectedPlanId(null);
      return;
    }
    if (!selectedPlanId || !plans.some((plan) => plan.id === selectedPlanId)) {
      setSelectedPlanId(plans[0].id);
    }
  }, [plans, selectedPlanId]);

  async function copyPlan(plan: FurnishingPlan) {
    const text = formatPlanForSharing(plan, input);
    try {
      await navigator.clipboard.writeText(text);
      setCopiedPlanId(plan.id);
      window.setTimeout(() => setCopiedPlanId(null), 1800);
    } catch {
      setCopiedPlanId(plan.id);
      window.setTimeout(() => setCopiedPlanId(null), 1800);
    }
  }

  async function saveCurrentPlan(plan: FurnishingPlan, copyLink: boolean) {
    setSavingPlanId(plan.id);
    try {
      await onSavePlan(plan, copyLink);
    } finally {
      setSavingPlanId(null);
    }
  }

  async function sendFeedback(planId: string, feedback: PlanFeedback) {
    setFeedbackByPlan((current) => ({ ...current, [planId]: feedback }));
    await onFeedback(planId, feedback);
  }

  if (error) {
    return (
      <ResultShell>
        <div className="plans-column state-panel">
          <div className="empty-state error-state">
            <span>Nešto ne radi</span>
            <h3>Nismo uspjeli složiti plan.</h3>
            <p>{error}</p>
            <small>Probaj ponovno. Ako razvijaš lokalno, provjeri jesu li backend i baza pokrenuti.</small>
          </div>
        </div>
      </ResultShell>
    );
  }

  if (isLoading) {
    return (
      <ResultShell>
        <div className="plans-column state-panel">
          <div className="empty-state loading-state">
            <span>Slažemo plan</span>
            <h3>Tražimo proizvode koji imaju smisla za tvoj budžet...</h3>
            <p>Provjeravamo prostoriju, trgovine, cijene i stvari koje si označio da već imaš.</p>
          </div>
        </div>
      </ResultShell>
    );
  }

  if (!plans.length) {
    return (
      <ResultShell>
        <div className="plans-column state-panel">
          <div className="empty-state friendly-empty-state">
            <span>Spremno</span>
            <h3>Tvoj plan će se prikazati ovdje.</h3>
            <p>Upiši s lijeve strane što želiš opremiti i klikni “Složi moj plan”.</p>
            <div className="empty-example">
              <strong>Primjer:</strong>
              <span>Imam 1500 € za dnevni boravak, želim svijetli izgled, samo IKEA i već imam TV.</span>
            </div>
          </div>
        </div>
      </ResultShell>
    );
  }

  const selectedPlan = plans.find((plan) => plan.id === selectedPlanId) ?? plans[0];
  const overBudget = selectedPlan.total > input.budget;
  const breakdown = getRetailerBreakdown(selectedPlan);
  const missing = missingCategories(selectedPlan, input);
  const selectedFeedback = feedbackByPlan[selectedPlan.id];

  return (
    <ResultShell>
      <div className="plans-column">
        <UnderstandingSummary input={input} />

        <div className="plan-choice-panel" aria-label="Odaberi verziju plana">
          <div>
            <span className="step-kicker">Usporedi</span>
            <h4>Odaberi verziju koja ti najbolje paše</h4>
          </div>
          <div className="plan-choice-grid">
            {plans.map((plan) => {
              const active = plan.id === selectedPlan.id;
              const planOverBudget = plan.total > input.budget;
              return (
                <button type="button" key={plan.id} className={active ? 'plan-choice-card active' : 'plan-choice-card'} onClick={() => setSelectedPlanId(plan.id)}>
                  <span>{plan.name}</span>
                  <strong>{formatCurrency(plan.total)}</strong>
                  <small>{planOverBudget ? `${formatCurrency(plan.total - input.budget)} iznad budžeta` : `${formatCurrency(input.budget - plan.total)} ostaje`}</small>
                </button>
              );
            })}
          </div>
        </div>

        <article className="plan-card focused-plan-card" key={selectedPlan.id}>
          <div className="plan-card-header">
            <div>
              <span className="plan-label">{selectedPlan.label}</span>
              <h3>{selectedPlan.name}</h3>
            </div>
            <div className={overBudget ? 'total over' : 'total'}>{formatCurrency(selectedPlan.total)}</div>
          </div>

          <div className="plan-summary-box">
            <span>Što dobivaš za ovaj budžet</span>
            <p>{selectedPlan.summary || defaultSummary(selectedPlan, input)}</p>
            <small>{budgetSentence(selectedPlan, input)}</small>
          </div>

          <div className="plan-total-card">
            <div>
              <span>Ukupno</span>
              <strong>{formatCurrency(selectedPlan.total)}</strong>
            </div>
            <div>
              <span>{overBudget ? 'Iznad budžeta' : 'Ostaje'}</span>
              <strong>{overBudget ? formatCurrency(selectedPlan.total - input.budget) : formatCurrency(input.budget - selectedPlan.total)}</strong>
            </div>
            <div>
              <span>Trgovine</span>
              <strong>{selectedPlan.retailersUsed.join(' + ')}</strong>
            </div>
          </div>

          <div className="plan-explainer-grid">
            <div>
              <span>Za koga je ovo dobro?</span>
              <p>{selectedPlan.goodFor || 'Dobro ako želiš brz, realan plan bez previše ručnog traženja po trgovinama.'}</p>
            </div>
            <div>
              <span>Na što treba paziti?</span>
              <p>{selectedPlan.tradeoff || selectedPlan.description}</p>
            </div>
          </div>

          <div className="quick-action-row" aria-label="Brze promjene plana">
            <button type="button" onClick={() => onQuickAction('cheaper', selectedPlan)}>Složi jeftinije</button>
            <button type="button" onClick={() => onQuickAction('nicer', selectedPlan)}>Napravi ljepše</button>
            <button type="button" onClick={() => onQuickAction('single-store', selectedPlan)}>Samo jedna trgovina</button>
            <button type="button" onClick={() => onQuickAction('least-stores', selectedPlan)}>Manje trgovina</button>
          </div>

          <div className="score-row enhanced-score-row">
            <div>
              <span>Koliko prati želje</span>
              <strong>{selectedPlan.fitScore}%</strong>
            </div>
            <div>
              <span>Usklađen izgled</span>
              <strong>{selectedPlan.styleConsistency}%</strong>
            </div>
            <div>
              <span>Kupnja</span>
              <strong>{effortLabels[selectedPlan.shoppingEffort]}</strong>
            </div>
            <div>
              <span>Broj proizvoda</span>
              <strong>{selectedPlan.items.length}</strong>
            </div>
          </div>

          <div className="retailer-breakdown">
            <div className="breakdown-title">Trošak po trgovini</div>
            {breakdown.map((entry) => (
              <div className="breakdown-row" key={entry.retailer}>
                <span>{entry.retailer}</span>
                <strong>{formatCurrency(entry.total)}</strong>
                <small>{entry.count} proizvoda</small>
              </div>
            ))}
          </div>

          {missing.length > 0 && (
            <div className="missing-box improved-missing-box">
              <strong>Još bi bilo dobro dodati:</strong>
              <span>{labelCategories(missing)}.</span>
              <p>Nismo ih dodali jer bi plan mogao otići iznad budžeta ili nema dovoljno dobrih proizvoda u odabranim trgovinama.</p>
              <button type="button" onClick={() => onQuickAction('nicer', selectedPlan)}>Pokušaj dodati svejedno</button>
            </div>
          )}

          <div className="items-list">
            {selectedPlan.items.map(({ product, reason }) => {
              const locked = lockedProductIds.includes(product.id);
              return (
                <div className={locked ? 'product-row locked' : 'product-row'} key={product.id}>
                  <img src={product.image} alt="" loading="lazy" />
                  <div className="product-info">
                    <div className="product-title-line">
                      <strong>{product.name}</strong>
                      <span>{formatCurrency(product.price)}</span>
                    </div>
                    <div className="meta-line">
                      <span>{product.retailer}</span>
                      <span>★ {product.rating}</span>
                      {product.originalPrice && <span>Akcija</span>}
                      {!product.inStock && <span>Nema na stanju</span>}
                      {locked && <span>Zadržano</span>}
                    </div>
                    <div className="product-reason-box">
                      <span>Zašto ovo?</span>
                      <p>{reason}</p>
                    </div>
                    <div className="product-actions">
                      <button type="button" onClick={() => onToggleLock(product.id)}>
                        {locked ? 'Ne moram zadržati' : 'Zadrži'}
                      </button>
                      <button type="button" onClick={() => onReplace(selectedPlan.id, product.id)} disabled={locked}>
                        Promijeni
                      </button>
                      <a href={product.url} target="_blank" rel="noreferrer" onClick={() => onProductClick(selectedPlan.id, product)}>
                        Pogledaj u trgovini
                      </a>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="feedback-card">
            <span>Je li ovaj plan dobar?</span>
            <div className="feedback-buttons">
              {feedbackOptions.map((option) => (
                <button type="button" key={option.value} className={selectedFeedback === option.value ? 'active' : ''} onClick={() => sendFeedback(selectedPlan.id, option.value)}>
                  {option.label}
                </button>
              ))}
            </div>
          </div>

          <div className="plan-actions">
            <button className="plan-button" type="button" onClick={() => saveCurrentPlan(selectedPlan, false)} disabled={savingPlanId === selectedPlan.id}>
              {savingPlanId === selectedPlan.id ? 'Spremam...' : 'Spremi plan'}
            </button>
            <button className="share-button" type="button" onClick={() => saveCurrentPlan(selectedPlan, true)} disabled={savingPlanId === selectedPlan.id}>
              Kopiraj link
            </button>
            <button className="share-button soft" type="button" onClick={() => copyPlan(selectedPlan)}>
              {copiedPlanId === selectedPlan.id ? 'Kopirano ✓' : 'Kopiraj popis za kupnju'}
            </button>
          </div>
        </article>
      </div>
    </ResultShell>
  );
}
