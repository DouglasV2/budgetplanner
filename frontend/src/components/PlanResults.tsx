import { useState, type ReactNode } from 'react';
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
  { value: 'wrong-style', label: 'Ne sviđa mi se stil' },
  { value: 'too-many-stores', label: 'Previše trgovina' }
];

function labelCategories(categories: ProductCategory[]) {
  if (!categories.length) return 'nema posebnih oznaka';
  return categories.map((category) => categoryLabels[category]).join(', ');
}

function missingCategories(plan: FurnishingPlan, input: PlannerInput) {
  return input.mustHaveCategories.filter((category) => !plan.items.some((item) => item.product.category === category));
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
          <span>Stil</span>
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
        <small>Ovdje se pojavi gotov popis proizvoda.</small>
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
              <span>Imam 1500 € za dnevni boravak, želim svijetli stil, samo IKEA i već imam TV.</span>
            </div>
          </div>
        </div>
      </ResultShell>
    );
  }

  return (
    <ResultShell>
      <div className="plans-column">
        <UnderstandingSummary input={input} />

        {plans.map((plan) => {
          const overBudget = plan.total > input.budget;
          const breakdown = getRetailerBreakdown(plan);
          const missing = missingCategories(plan, input);
          const selectedFeedback = feedbackByPlan[plan.id];

          return (
            <article className="plan-card" key={plan.id}>
              <div className="plan-card-header">
                <div>
                  <span className="plan-label">{plan.label}</span>
                  <h3>{plan.name}</h3>
                </div>
                <div className={overBudget ? 'total over' : 'total'}>{formatCurrency(plan.total)}</div>
              </div>

              <div className="plan-total-card">
                <div>
                  <span>Ukupno</span>
                  <strong>{formatCurrency(plan.total)}</strong>
                </div>
                <div>
                  <span>{overBudget ? 'Iznad budžeta' : 'Ostaje'}</span>
                  <strong>{overBudget ? formatCurrency(plan.total - input.budget) : formatCurrency(input.budget - plan.total)}</strong>
                </div>
                <div>
                  <span>Trgovine</span>
                  <strong>{plan.retailersUsed.join(' + ')}</strong>
                </div>
              </div>

              <p className="plan-description">{plan.description}</p>

              <div className="quick-action-row" aria-label="Brze promjene plana">
                <button type="button" onClick={() => onQuickAction('cheaper', plan)}>Složi jeftinije</button>
                <button type="button" onClick={() => onQuickAction('nicer', plan)}>Napravi ljepše</button>
                <button type="button" onClick={() => onQuickAction('single-store', plan)}>Samo jedna trgovina</button>
                <button type="button" onClick={() => onQuickAction('least-stores', plan)}>Manje trgovina</button>
              </div>

              <div className="score-row enhanced-score-row">
                <div>
                  <span>Koliko dobro prati želje</span>
                  <strong>{plan.fitScore}%</strong>
                </div>
                <div>
                  <span>Usklađen izgled</span>
                  <strong>{plan.styleConsistency}%</strong>
                </div>
                <div>
                  <span>Koliko je kupnja jednostavna</span>
                  <strong>{effortLabels[plan.shoppingEffort]}</strong>
                </div>
                <div>
                  <span>Broj proizvoda</span>
                  <strong>{plan.items.length}</strong>
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
                <div className="missing-box">
                  <strong>Ovo nije ušlo u plan:</strong> {labelCategories(missing)}. Probaj povećati budžet ili dodati još trgovina.
                </div>
              )}

              <div className="items-list">
                {plan.items.map(({ product, reason }) => {
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
                        <p>{reason}</p>
                        <div className="product-actions">
                          <button type="button" onClick={() => onToggleLock(product.id)}>
                            {locked ? 'Ne moram zadržati' : 'Zadrži'}
                          </button>
                          <button type="button" onClick={() => onReplace(plan.id, product.id)} disabled={locked}>
                            Promijeni
                          </button>
                          <a href={product.url} target="_blank" rel="noreferrer" onClick={() => onProductClick(plan.id, product)}>
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
                    <button
                      type="button"
                      key={option.value}
                      className={selectedFeedback === option.value ? 'active' : ''}
                      onClick={() => sendFeedback(plan.id, option.value)}
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="plan-actions">
                <button className="plan-button" type="button" onClick={() => saveCurrentPlan(plan, false)} disabled={savingPlanId === plan.id}>
                  {savingPlanId === plan.id ? 'Spremam...' : 'Spremi plan'}
                </button>
                <button className="share-button" type="button" onClick={() => saveCurrentPlan(plan, true)} disabled={savingPlanId === plan.id}>
                  Kopiraj link
                </button>
                <button className="share-button soft" type="button" onClick={() => copyPlan(plan)}>
                  {copiedPlanId === plan.id ? 'Kopirano ✓' : 'Kopiraj popis za kupnju'}
                </button>
              </div>
            </article>
          );
        })}
      </div>
    </ResultShell>
  );
}
