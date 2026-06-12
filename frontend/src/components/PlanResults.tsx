import { useEffect, useState, type ReactNode } from 'react';
import type {
  FurnishingPlan,
  PlanFeedback,
  PlannerInput,
  Product,
  ProductCategory,
  RoomType,
  PlanItem,
  ShoppingPriority,
  ReplacementChoice
} from '../types';
import {
  categoryLabels,
  formatCurrency,
  formatPlanForSharing,
  furnishingLevelLabels,
  getRetailerBreakdown,
  roomLabels,
  shoppingPriorityLabels,
  styleLabels
} from '../utils/planner';

export type QuickPlanAction = 'cheaper' | 'nicer' | 'single-store' | 'least-stores';

interface PlanResultsProps {
  plans: FurnishingPlan[];
  input: PlannerInput;
  onReplace: (planId: string, productId: string, changeType?: ReplacementChoice) => void;
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

// Room specific category order. The order follows the way a person usually buys:
// big pieces first, then comfort, then details.
const ROOM_CATEGORY_ORDER: Record<RoomType, ProductCategory[]> = {
  'living-room': ['sofa', 'tv-unit', 'table', 'rug', 'lighting', 'storage', 'decor'],
  'home-office': ['desk', 'chair', 'storage', 'lighting', 'decor'],
  bedroom: ['bed', 'mattress', 'storage', 'lighting', 'decor'],
  'home-gym': ['gym-equipment', 'storage', 'lighting', 'decor']
};

const TIER_LABELS: Record<string, string> = {
  'Najbolji izbor': 'Udobnije',
  'Najjeftinije': 'Osnovno',
  'Ljepša verzija': 'Kompletno'
};

const STEP_ORDER = ['buy-first', 'add-comfort', 'later'] as const;

const STEP_TEXT = {
  'buy-first': {
    title: '1. Najvažnije za početak',
    shortTitle: 'Najvažnije',
    description: 'Ovo su komadi koji najviše nose prostor i cijeli plan.'
  },
  'add-comfort': {
    title: '2. Za ugodniji prostor',
    shortTitle: 'Ugodnije',
    description: 'Ovo daje topliji i dovršeniji osjećaj, ali ne mora biti prva odluka.'
  },
  later: {
    title: '3. Može kasnije',
    shortTitle: 'Može kasnije',
    description: 'Ovo je lijepo imati, ali mirno može pričekati ako želiš čuvati budžet.'
  }
};

const CORE_BY_ROOM: Record<RoomType, ProductCategory[]> = {
  'living-room': ['sofa', 'tv-unit', 'table'],
  'home-office': ['desk', 'chair'],
  bedroom: ['bed', 'mattress'],
  'home-gym': ['gym-equipment']
};

function priorityForItem(item: PlanItem, roomType: RoomType): ShoppingPriority {
  if (item.shoppingPriority) return item.shoppingPriority;
  if (CORE_BY_ROOM[roomType]?.includes(item.product.category)) return 'buy-first';
  if (['rug', 'lighting', 'storage'].includes(item.product.category)) return 'add-comfort';
  return 'later';
}

function purchaseSteps(plan: FurnishingPlan, roomType: RoomType) {
  return STEP_ORDER.map((priority) => {
    const items = plan.items.filter((item) => priorityForItem(item, roomType) === priority);
    return {
      priority,
      items,
      subtotal: items.reduce((sum, item) => sum + item.product.price, 0),
      ...STEP_TEXT[priority]
    };
  }).filter((step) => step.items.length > 0);
}

function desiredCategoriesForLevel(input: PlannerInput) {
  const all = ROOM_CATEGORY_ORDER[input.roomType] ?? [];
  const core = CORE_BY_ROOM[input.roomType] ?? [];
  const level = input.furnishingLevel ?? 'comfort';
  const desired = all.filter((category) => {
    if (core.includes(category)) return true;
    if (['rug', 'lighting', 'storage'].includes(category)) return level === 'comfort' || level === 'complete';
    return level === 'complete';
  });
  return Array.from(new Set([...desired, ...input.mustHaveCategories]));
}

function missingForRoom(plan: FurnishingPlan, input: PlannerInput) {
  return desiredCategoriesForLevel(input).filter(
    (category) =>
      !plan.items.some((item) => item.product.category === category) &&
      !input.alreadyHaveCategories.includes(category)
  );
}

function defaultSummary(plan: FurnishingPlan, input: PlannerInput) {
  const room = roomLabels[input.roomType];
  const firstStep = purchaseSteps(plan, input.roomType)[0];
  const firstItems = firstStep?.items
    .slice(0, 3)
    .map((item) => categoryLabels[item.product.category].toLowerCase())
    .join(', ');
  return `Za ${formatCurrency(input.budget)} najviše smisla imaju ${firstItems || 'osnovni komadi'} za ${room}. Plan koristi ${plan.retailersUsed.length === 1 ? 'jednu trgovinu' : `${plan.retailersUsed.length} trgovine`} i ${plan.total <= input.budget ? 'ostaje unutar budžeta' : 'treba još smanjiti da bude sigurniji'}.`;
}

function preferredPlanId(plans: FurnishingPlan[], input: PlannerInput) {
  if (!plans.length) return null;
  const preferredName = input.optimizationGoal === 'lowest-price' || input.furnishingLevel === 'basic'
    ? 'Najjeftinije'
    : input.optimizationGoal === 'style-match' || input.furnishingLevel === 'complete'
    ? 'Ljepša verzija'
    : 'Najbolji izbor';
  return plans.find((plan) => plan.name === preferredName)?.id ?? plans.find((plan) => plan.name === 'Najbolji izbor')?.id ?? plans[0].id;
}

function firstBuyText(steps: ReturnType<typeof purchaseSteps>) {
  const first = steps.find((step) => step.priority === 'buy-first') ?? steps[0];
  const items = first?.items
    .slice(0, 3)
    .map((item) => categoryLabels[item.product.category].toLowerCase())
    .join(', ');
  return items || 'osnovne komade';
}

function laterText(steps: ReturnType<typeof purchaseSteps>) {
  const later = steps.find((step) => step.priority === 'later');
  if (later?.items.length) {
    return later.items
      .slice(0, 2)
      .map((item) => categoryLabels[item.product.category].toLowerCase())
      .join(', ');
  }
  const comfort = steps.find((step) => step.priority === 'add-comfort');
  return comfort?.items[0] ? categoryLabels[comfort.items[0].product.category].toLowerCase() : 'sitnice';
}

function decisionLabel(plan: FurnishingPlan, input: PlannerInput) {
  const difference = input.budget - plan.total;
  if (difference < 0) return 'Još nije idealno';
  if (difference >= input.budget * 0.12) return 'Isplativo i sigurno';
  return 'Isplativo, ali blizu budžeta';
}

function decisionHeadline(plan: FurnishingPlan, input: PlannerInput, steps: ReturnType<typeof purchaseSteps>) {
  const first = firstBuyText(steps);
  const later = laterText(steps);
  if (plan.total > input.budget) {
    return `Plan je dobar, ali još treba smanjiti ${formatCurrency(plan.total - input.budget)}.`;
  }
  return `Za ${formatCurrency(input.budget)} najisplativije je fokusirati se na ${first}; ${later} može čekati.`;
}

function shortBudgetText(plan: FurnishingPlan, input: PlannerInput) {
  const difference = input.budget - plan.total;
  if (difference >= 0) return `Ostaje ${formatCurrency(difference)}`;
  return `${formatCurrency(Math.abs(difference))} iznad`;
}


function ShoppingListCard({ plan, steps }: { plan: FurnishingPlan; steps: ReturnType<typeof purchaseSteps> }) {
  const retailerText = plan.retailersUsed.length <= 1 ? plan.retailersUsed[0] || 'jedna trgovina' : plan.retailersUsed.join(' + ');

  return (
    <section className="shopping-list-card" aria-label="Popis za kupnju">
      <div className="shopping-list-head">
        <div>
          <span>Popis za kupnju</span>
          <strong>{plan.items.length} proizvoda · {retailerText}</strong>
        </div>
        <strong>{formatCurrency(plan.total)}</strong>
      </div>
      <div className="shopping-list-groups">
        {steps.map((step) => (
          <div className="shopping-list-group" key={step.priority}>
            <div className="shopping-list-group-title">
              <span>{step.shortTitle}</span>
              <strong>{formatCurrency(step.subtotal)}</strong>
            </div>
            <ul>
              {step.items.map((item) => (
                <li key={item.product.id}>
                  <span>{item.product.name}</span>
                  <small>{item.product.retailer}</small>
                  <strong>{formatCurrency(item.product.price)}</strong>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  );
}

function UnderstandingSummary({ input }: { input: PlannerInput }) {
  return (
    <details className="understood-card compact-understood-card">
      <summary>
        <span>Što smo uzeli u obzir</span>
        <strong>{formatCurrency(input.budget)} · {roomLabels[input.roomType]} · {styleLabels[input.style]}</strong>
      </summary>
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
          <span>Razina</span>
          <strong>{furnishingLevelLabels[input.furnishingLevel ?? 'comfort']}</strong>
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
    </details>
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
        <small>U 10 sekundi treba biti jasno što dobivaš, koliko košta i što je najisplativije.</small>
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
  const [expandedProductId, setExpandedProductId] = useState<string | null>(null);
  const [dislikeProductId, setDislikeProductId] = useState<string | null>(null);

  useEffect(() => {
    setSelectedPlanId(preferredPlanId(plans, input));
    setExpandedProductId(null);
    setDislikeProductId(null);
  }, [plans, input.optimizationGoal, input.furnishingLevel]);

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
            <h3>Tražimo što se stvarno isplati kupiti...</h3>
            <p>Prvo gledamo glavne komade, zatim udobnost, pa detalje samo ako budžet drži.</p>
          </div>
        </div>
      </ResultShell>
    );
  }

  if (!plans.length) {
    return (
      <ResultShell>
        <div className="plans-column state-panel">
          <div className="empty-state friendly-empty-state decision-empty-state">
            <span>Spremno</span>
            <h3>Ovdje ćeš odmah vidjeti što se isplati.</h3>
            <p>Plan će prvo pokazati ukupnu cijenu, najvažnije proizvode i stvari koje mogu čekati da odmah vidiš što je isplativo.</p>
            <div className="empty-example">
              <strong>Dobit ćeš:</strong>
              <span>1. najvažnije · 2. ugodniji prostor · 3. stvari koje mogu kasnije</span>
            </div>
          </div>
        </div>
      </ResultShell>
    );
  }

  const selectedPlan = plans.find((plan) => plan.id === selectedPlanId) ?? plans.find((plan) => plan.name === 'Najbolji izbor') ?? plans[0];
  const overBudget = selectedPlan.total > input.budget;
  const breakdown = getRetailerBreakdown(selectedPlan);
  const missing = missingForRoom(selectedPlan, input);
  const steps = purchaseSteps(selectedPlan, input.roomType);
  const tier = TIER_LABELS[selectedPlan.name] ?? furnishingLevelLabels[input.furnishingLevel ?? 'comfort'];
  const selectedFeedback = feedbackByPlan[selectedPlan.id];
  const primaryStep = steps.find((step) => step.priority === 'buy-first') ?? steps[0];

  return (
    <ResultShell>
      <div className="plans-column decision-results-column">
        <article className="plan-card focused-plan-card decision-plan-card" key={selectedPlan.id}>
          <div className="decision-card">
            <div className="decision-topline">
              <span>{decisionLabel(selectedPlan, input)}</span>
              <strong>{selectedPlan.name}</strong>
            </div>
            <h3>{decisionHeadline(selectedPlan, input, steps)}</h3>
            <p>{selectedPlan.advisorNote || defaultSummary(selectedPlan, input)}</p>

            <div className="decision-metrics" aria-label="Najvažnije o planu">
              <div>
                <span>Ukupno</span>
                <strong>{formatCurrency(selectedPlan.total)}</strong>
              </div>
              <div>
                <span>{overBudget ? 'Treba smanjiti' : 'Sigurnost'}</span>
                <strong className={overBudget ? 'over-text' : ''}>{shortBudgetText(selectedPlan, input)}</strong>
              </div>
              <div>
                <span>Trgovine</span>
                <strong>{selectedPlan.retailersUsed.length || 0}</strong>
              </div>
            </div>

            {primaryStep && (
              <div className="first-buy-strip">
                <span>Najvažnije u planu</span>
                <strong>{primaryStep.items.map((item) => item.product.name).join(' + ')}</strong>
              </div>
            )}

            <div className="decision-actions">
              <button className="plan-button primary-copy-button" type="button" onClick={() => copyPlan(selectedPlan)}>
                {copiedPlanId === selectedPlan.id ? 'Popis kopiran ✓' : 'Kopiraj popis za kupnju'}
              </button>
              <button className="share-button soft" type="button" onClick={() => saveCurrentPlan(selectedPlan, false)} disabled={savingPlanId === selectedPlan.id}>
                {savingPlanId === selectedPlan.id ? 'Spremam...' : 'Spremi u moje planove'}
              </button>
              <button className="share-button soft" type="button" onClick={() => saveCurrentPlan(selectedPlan, true)} disabled={savingPlanId === selectedPlan.id}>
                Kopiraj link
              </button>
            </div>
          </div>

          <div className="quick-action-row decision-quick-actions" aria-label="Brze promjene plana">
            <button type="button" onClick={() => onQuickAction('cheaper', selectedPlan)}>Treba jeftinije</button>
            <button type="button" onClick={() => onQuickAction('nicer', selectedPlan)}>Želim ljepše</button>
            <button type="button" onClick={() => onQuickAction('single-store', selectedPlan)}>Samo jedna trgovina</button>
            <button type="button" onClick={() => onQuickAction('least-stores', selectedPlan)}>Manje trgovina</button>
          </div>

          <ShoppingListCard plan={selectedPlan} steps={steps} />

          <div className="shopping-steps-card main-shopping-steps-card">
            <div className="shopping-steps-heading">
              <span>Plan po prioritetima</span>
              <p>Ovo pokazuje što najviše nosi plan, a što je više stvar dojma ili kasnije nadogradnje.</p>
            </div>
            <div className="shopping-steps-list">
              {steps.map((step) => (
                <div className="shopping-step-row" key={step.priority}>
                  <div className="step-number">{step.title.split('.')[0]}</div>
                  <div>
                    <strong>{step.shortTitle}</strong>
                    <p>{step.description}</p>
                    <small>{step.items.map((item) => item.product.name).join(' + ')}</small>
                  </div>
                  <span>{formatCurrency(step.subtotal)}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="items-list step-items-list">
            <div className="result-section-heading">
              <span>Proizvodi u ovom planu</span>
              <p>Ovdje su konkretne stvari koje dobivaš. Zamjene otvori samo ako ti nešto ne odgovara.</p>
            </div>
            {steps.map((step) => (
              <section className="step-product-section" key={step.priority}>
                <div className="step-product-section-title">
                  <span>{step.title}</span>
                  <strong>{formatCurrency(step.subtotal)}</strong>
                </div>
                {step.items.map((item) => {
                  const { product, reason } = item;
                  const locked = lockedProductIds.includes(product.id);
                  const priority = priorityForItem(item, input.roomType);
                  const expanded = expandedProductId === product.id;
                  return (
                    <div className={locked ? 'product-row locked decision-product-row' : 'product-row decision-product-row'} key={product.id}>
                      <img src={product.image} alt="" loading="lazy" />
                      <div className="product-info">
                        <div className="product-title-line">
                          <strong>{product.name}</strong>
                          <span>{formatCurrency(product.price)}</span>
                        </div>
                        <div className="meta-line">
                          <span>{product.retailer}</span>
                          <span className={`priority-chip ${priority}`}>{shoppingPriorityLabels[priority]}</span>
                          <span>★ {product.rating}</span>
                          {product.originalPrice && <span>Akcija</span>}
                          {!product.inStock && <span>Nema na stanju</span>}
                          {locked && <span>Zadržano</span>}
                        </div>
                        <div className="product-reason-box compact-reason-box">
                          <span>{item.shoppingRole || 'Zašto ovo?'}</span>
                          <p>{reason}</p>
                        </div>
                        <div className="product-actions decision-product-actions">
                          <button type="button" onClick={() => onToggleLock(product.id)}>
                            {locked ? 'Ne moram zadržati' : 'Zadrži'}
                          </button>
                          <button type="button" onClick={() => setExpandedProductId(expanded ? null : product.id)} disabled={locked}>
                            {expanded ? 'Sakrij zamjene' : 'Promijeni'}
                          </button>
                          <a href={product.url} target="_blank" rel="noreferrer" onClick={() => onProductClick(selectedPlan.id, product)}>
                            Pogledaj u trgovini
                          </a>
                        </div>
                        {expanded && (
                          <div className="replacement-menu" aria-label="Odaberi što želiš promijeniti">
                            <button type="button" onClick={() => onReplace(selectedPlan.id, product.id, 'cheaper')}>Nađi jeftinije</button>
                            <button type="button" onClick={() => onReplace(selectedPlan.id, product.id, 'nicer')}>Nađi ljepše</button>
                            <button type="button" onClick={() => setDislikeProductId(dislikeProductId === product.id ? null : product.id)}>Ne sviđa mi se</button>
                            <button type="button" onClick={() => onReplace(selectedPlan.id, product.id, 'remove')}>Ne treba mi ovo</button>
                            {dislikeProductId === product.id && (
                              <div className="dislike-reasons">
                                <span>Što ne odgovara?</span>
                                <button type="button" onClick={() => onReplace(selectedPlan.id, product.id, 'cheaper')}>Preskupo je</button>
                                <button type="button" onClick={() => onReplace(selectedPlan.id, product.id, 'nicer')}>Želim ljepši stil</button>
                                <button type="button" onClick={() => onReplace(selectedPlan.id, product.id, 'different')}>Pokaži drugu opciju</button>
                                <button type="button" onClick={() => onReplace(selectedPlan.id, product.id, 'remove')}>Ne treba mi ta stvar</button>
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </section>
            ))}
          </div>

          <details className="alternate-plans-panel">
            <summary>
              <span>Želiš drugačije?</span>
              <strong>Usporedi jeftiniju ili ljepšu verziju</strong>
            </summary>
            <div className="plan-choice-grid compact-plan-choice-grid">
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
          </details>

          <details className="secondary-info-panel">
            <summary>
              <span>Detalji plana</span>
              <strong>Zašto je ovo odabrano i gdje ide novac</strong>
            </summary>

            <div className="plan-summary-box">
              <span>Što dobivaš za ovaj budžet</span>
              <p>{selectedPlan.summary || defaultSummary(selectedPlan, input)}</p>
              <small>{selectedPlan.budgetStatus}</small>
            </div>

            <div className="plan-card-header compact-plan-header">
              <div>
                <span className="plan-label">{selectedPlan.label}</span>
                <h3>{selectedPlan.name}</h3>
                {tier && <small className="plan-tier">{tier} oprema</small>}
              </div>
              <div className={overBudget ? 'total over' : 'total'}>{formatCurrency(selectedPlan.total)}</div>
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

            <div className="tradeoff-panel" aria-label="Savjeti za budžet">
              <div className="tradeoff-card save">
                <span>Kako spustiti cijenu</span>
                <ul>
                  {(selectedPlan.savingTips?.length ? selectedPlan.savingTips : ['Ako je budžet tijesan, prvo odgodi detalje i čuvaj novac za glavne komade.']).map((tip) => (
                    <li key={tip}>{tip}</li>
                  ))}
                </ul>
              </div>
              <div className="tradeoff-card upgrade">
                <span>Ako možeš dodati još malo</span>
                <ul>
                  {(selectedPlan.upgradeTips?.length ? selectedPlan.upgradeTips : ['Najviše se osjeti nadogradnja rasvjete, tepiha ili glavnog komada koji koristiš svaki dan.']).map((tip) => (
                    <li key={tip}>{tip}</li>
                  ))}
                </ul>
              </div>
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
                <strong>Preskočeno za sada</strong>
                <p>Ovo nije ušlo jer bi moglo probiti budžet ili trenutno nema dovoljno dobru opciju u katalogu:</p>
                <ul className="missing-list">
                  {missing.map((category) => (
                    <li key={category}>
                      <span>{categoryLabels[category]}</span>
                      <button type="button" onClick={() => onQuickAction('nicer', selectedPlan)}>Dodaj u ljepšu verziju</button>
                    </li>
                  ))}
                </ul>
                <small>Ovo ti pomaže odvojiti što kupiti sada, a što mirno može čekati kasnije.</small>
              </div>
            )}
          </details>

          <UnderstandingSummary input={input} />

          <div className="feedback-card compact-feedback-card">
            <span>Je li ovaj plan dobar?</span>
            <div className="feedback-buttons">
              {feedbackOptions.map((option) => (
                <button type="button" key={option.value} className={selectedFeedback === option.value ? 'active' : ''} onClick={() => sendFeedback(selectedPlan.id, option.value)}>
                  {option.label}
                </button>
              ))}
            </div>
          </div>
        </article>
      </div>
    </ResultShell>
  );
}
