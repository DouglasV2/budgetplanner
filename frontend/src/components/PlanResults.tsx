import { useState } from 'react';
import type { FurnishingPlan, PlannerInput, ProductCategory } from '../types';
import { categoryLabels, formatCurrency, formatPlanForSharing, getRetailerBreakdown, roomLabels, styleLabels } from '../utils/planner';

export type QuickPlanAction = 'cheaper' | 'nicer' | 'single-store' | 'least-stores';

interface PlanResultsProps {
  plans: FurnishingPlan[];
  input: PlannerInput;
  onReplace: (planId: string, productId: string) => void;
  onToggleLock: (productId: string) => void;
  lockedProductIds: string[];
  onQuickAction: (action: QuickPlanAction, plan?: FurnishingPlan) => void;
  isLoading?: boolean;
  error?: string | null;
}

const effortLabels = {
  Low: 'Nizak effort',
  Medium: 'Srednji effort',
  High: 'Visok effort'
};

function labelCategories(categories: ProductCategory[]) {
  if (!categories.length) return 'nije posebno označeno';
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
        <strong>{formatCurrency(input.budget)} budget</strong>
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
        <span>Treba: {labelCategories(input.mustHaveCategories)}</span>
        <span>Već ima: {labelCategories(input.alreadyHaveCategories)}</span>
        {input.lockedProductIds.length > 0 && <span>Zaključano: {input.lockedProductIds.length} proizvoda</span>}
      </div>
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
  isLoading = false,
  error = null
}: PlanResultsProps) {
  const [copiedPlanId, setCopiedPlanId] = useState<string | null>(null);

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

  if (error) {
    return (
      <div className="plans-column state-panel">
        <div className="empty-state error-state">
          <span>API error</span>
          <h3>Backend nije vratio plan.</h3>
          <p>{error}</p>
          <code>cd backend && mvn spring-boot:run</code>
          <small>Potrebni su Java 17+ i Maven instalirani lokalno.</small>
        </div>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="plans-column state-panel">
        <div className="empty-state loading-state">
          <span>Generating</span>
          <h3>Slažem tri plana iz product baze...</h3>
          <p>Backend parsira prompt, bira kategorije, filtrira trgovine i optimizira budžet.</p>
        </div>
      </div>
    );
  }

  if (!plans.length) {
    return (
      <div className="plans-column state-panel">
        <div className="empty-state">
          <span>Ready</span>
          <h3>Upiši prompt i generiraj prvi full-stack plan.</h3>
          <p>Primjer: “Imam 1500 € za dnevni boravak, samo IKEA, već imam TV, trebam kauč i tepih.”</p>
        </div>
      </div>
    );
  }

  return (
    <div className="plans-column">
      <UnderstandingSummary input={input} />

      {plans.map((plan) => {
        const overBudget = plan.total > input.budget;
        const breakdown = getRetailerBreakdown(plan);
        const missing = missingCategories(plan, input);

        return (
          <article className="plan-card" key={plan.id}>
            <div className="plan-card-header">
              <div>
                <span className="plan-label">{plan.label}</span>
                <h3>{plan.name}</h3>
              </div>
              <div className={overBudget ? 'total over' : 'total'}>{formatCurrency(plan.total)}</div>
            </div>

            <p className="plan-description">{plan.description}</p>

            <div className="quick-action-row" aria-label="Quick plan actions">
              <button type="button" onClick={() => onQuickAction('cheaper', plan)}>Učini jeftinije</button>
              <button type="button" onClick={() => onQuickAction('nicer', plan)}>Učini ljepše</button>
              <button type="button" onClick={() => onQuickAction('single-store', plan)}>Samo jedna trgovina</button>
              <button type="button" onClick={() => onQuickAction('least-stores', plan)}>Manje trgovina</button>
            </div>

            <div className="score-row enhanced-score-row">
              <div>
                <span>Fit score</span>
                <strong>{plan.fitScore}%</strong>
              </div>
              <div>
                <span>Style match</span>
                <strong>{plan.styleConsistency}%</strong>
              </div>
              <div>
                <span>Shopping effort</span>
                <strong>{effortLabels[plan.shoppingEffort]}</strong>
              </div>
              <div>
                <span>{overBudget ? 'Iznad budžeta' : 'Ostaje'}</span>
                <strong>{overBudget ? formatCurrency(plan.total - input.budget) : formatCurrency(plan.savings)}</strong>
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
                <strong>Nedostaje u ovom planu:</strong> {labelCategories(missing)}. Probaj Stretch plan ili povećaj budžet.
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
                        {locked && <span>Zaključano</span>}
                      </div>
                      <p>{reason}</p>
                      <div className="product-actions">
                        <button type="button" onClick={() => onToggleLock(product.id)}>
                          {locked ? 'Otključaj' : 'Zaključaj'}
                        </button>
                        <button type="button" onClick={() => onReplace(plan.id, product.id)} disabled={locked}>
                          Zamijeni
                        </button>
                        <a href={product.url} target="_blank" rel="noreferrer">
                          Otvori trgovinu
                        </a>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="plan-actions">
              <button className="plan-button" type="button">
                Spremi plan
              </button>
              <button className="share-button" type="button" onClick={() => copyPlan(plan)}>
                {copiedPlanId === plan.id ? 'Kopirano ✓' : 'Kopiraj shopping listu'}
              </button>
            </div>
          </article>
        );
      })}
    </div>
  );
}
