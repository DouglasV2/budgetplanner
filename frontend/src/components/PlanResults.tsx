import { useEffect, useState, type ReactNode, type SyntheticEvent } from 'react';
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
  isCheckInStore,
  resolveStoreTrip,
  roomLabels,
  shoppingPriorityLabels,
  storeCountText,
  styleLabels
} from '../utils/planner';
import { useLocale } from '../LocaleContext';

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
  partialNotice?: string | null;
}

type Translate = (key: string, params?: Record<string, string | number>) => string;

const effortLabelKeys: Record<string, string> = {
  Low: 'results.effortLow',
  Medium: 'results.effortMedium',
  High: 'results.effortHigh'
};

const feedbackOptions: Array<{ value: PlanFeedback; labelKey: string }> = [
  { value: 'useful', labelKey: 'results.feedbackUseful' },
  { value: 'too-expensive', labelKey: 'results.feedbackTooExpensive' },
  { value: 'wrong-style', labelKey: 'results.feedbackWrongStyle' },
  { value: 'too-many-stores', labelKey: 'results.feedbackTooManyStores' }
];

function labelCategories(t: Translate, categories: ProductCategory[]) {
  if (!categories.length) return t('results.noSpecialTags');
  return categories.map((category) => categoryLabels[category]).join(', ');
}

// Room specific category order. The order follows the way a person usually buys:
// big pieces first, then comfort, then details.
const ROOM_CATEGORY_ORDER: Record<RoomType, ProductCategory[]> = {
  'living-room': ['sofa', 'tv-unit', 'table', 'rug', 'lighting', 'storage', 'decor'],
  'home-office': ['desk', 'chair', 'storage', 'lighting', 'decor'],
  bedroom: ['bed', 'mattress', 'nightstand', 'wardrobe', 'dresser', 'storage', 'lighting', 'decor'],
  'home-gym': ['gym-equipment', 'storage', 'lighting', 'decor'],
  kitchen: ['kitchen-cart', 'kitchen-storage', 'lighting', 'storage', 'decor'],
  'dining-room': ['dining-table', 'dining-chair', 'lighting', 'rug', 'storage', 'decor'],
  hallway: ['storage', 'lighting', 'rug', 'decor'],
  bathroom: ['storage', 'lighting', 'decor']
};

const TIER_LABEL_KEYS: Record<string, string> = {
  'Najbolji izbor': 'results.tierComfort',
  'Najjeftinije': 'results.tierBasic',
  'Ljepša verzija': 'results.tierComplete'
};

const STEP_ORDER = ['buy-first', 'add-comfort', 'later'] as const;

const STEP_TEXT = {
  'buy-first': { titleKey: 'results.stepBuyFirstTitle' },
  'add-comfort': { titleKey: 'results.stepAddComfortTitle' },
  later: { titleKey: 'results.stepLaterTitle' }
};

const CORE_BY_ROOM: Record<RoomType, ProductCategory[]> = {
  'living-room': ['sofa', 'tv-unit', 'table'],
  'home-office': ['desk', 'chair'],
  bedroom: ['bed', 'mattress'],
  'home-gym': ['gym-equipment'],
  kitchen: ['kitchen-cart'],
  'dining-room': ['dining-table', 'dining-chair'],
  hallway: ['storage'],
  bathroom: ['storage']
};

const FALLBACK_IMAGES: Record<ProductCategory, string> = {
  sofa: 'https://images.unsplash.com/photo-1555041469-a586c61ea9bc?auto=format&fit=crop&w=900&q=80',
  chair: 'https://images.unsplash.com/photo-1589384267710-7a25bc5ca5f3?auto=format&fit=crop&w=900&q=80',
  table: 'https://images.unsplash.com/photo-1532372320572-cda25653a694?auto=format&fit=crop&w=900&q=80',
  'tv-unit': 'https://images.unsplash.com/photo-1615873968403-89e068629265?auto=format&fit=crop&w=900&q=80',
  storage: 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=900&q=80',
  rug: 'https://images.unsplash.com/photo-1600166898405-da9535204843?auto=format&fit=crop&w=900&q=80',
  lighting: 'https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=900&q=80',
  decor: 'https://images.unsplash.com/photo-1513519245088-0e12902e5a38?auto=format&fit=crop&w=900&q=80',
  desk: 'https://images.unsplash.com/photo-1518455027359-f3f8164ba6bd?auto=format&fit=crop&w=900&q=80',
  bed: 'https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=900&q=80',
  mattress: 'https://images.unsplash.com/photo-1631049307264-da0ec9d70304?auto=format&fit=crop&w=900&q=80',
  'gym-equipment': 'https://images.unsplash.com/photo-1583454110551-21f2fa2afe61?auto=format&fit=crop&w=900&q=80',
  // New rooms (Sprint 10.7): reuse the closest existing category placeholder image.
  'dining-table': 'https://images.unsplash.com/photo-1532372320572-cda25653a694?auto=format&fit=crop&w=900&q=80',
  'dining-chair': 'https://images.unsplash.com/photo-1589384267710-7a25bc5ca5f3?auto=format&fit=crop&w=900&q=80',
  'kitchen-storage': 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=900&q=80',
  'kitchen-cart': 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=900&q=80',
  nightstand: 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=900&q=80',
  wardrobe: 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=900&q=80',
  dresser: 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=900&q=80'
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

function defaultSummary(t: Translate, plan: FurnishingPlan, input: PlannerInput) {
  const room = roomLabels[input.roomType];
  const firstStep = purchaseSteps(plan, input.roomType)[0];
  const firstItems = firstStep?.items
    .slice(0, 3)
    .map((item) => categoryLabels[item.product.category].toLowerCase())
    .join(', ');
  const stores = plan.retailersUsed.length === 1
    ? t('results.summaryOneStore')
    : t('results.summaryManyStores', { count: plan.retailersUsed.length });
  const status = plan.total <= input.budget
    ? t('results.summaryWithinBudget')
    : t('results.summaryNeedsTrim');
  return t('results.defaultSummary', {
    budget: formatCurrency(input.budget),
    items: firstItems || t('results.basicPieces'),
    room,
    stores,
    status
  });
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

function firstBuyText(t: Translate, steps: ReturnType<typeof purchaseSteps>) {
  const first = steps.find((step) => step.priority === 'buy-first') ?? steps[0];
  const items = first?.items
    .slice(0, 3)
    .map((item) => categoryLabels[item.product.category].toLowerCase())
    .join(', ');
  return items || t('results.basicPiecesAcc');
}

function laterText(t: Translate, steps: ReturnType<typeof purchaseSteps>) {
  const later = steps.find((step) => step.priority === 'later');
  if (later?.items.length) {
    return later.items
      .slice(0, 2)
      .map((item) => categoryLabels[item.product.category].toLowerCase())
      .join(', ');
  }
  const comfort = steps.find((step) => step.priority === 'add-comfort');
  return comfort?.items[0] ? categoryLabels[comfort.items[0].product.category].toLowerCase() : t('results.smallStuff');
}

function decisionLabel(t: Translate, plan: FurnishingPlan, input: PlannerInput) {
  const difference = input.budget - plan.total;
  if (difference < 0) return t('results.decisionNotIdeal');
  if (difference >= input.budget * 0.12) return t('results.decisionWorthSafe');
  return t('results.decisionWorthClose');
}

function decisionHeadline(t: Translate, plan: FurnishingPlan, input: PlannerInput, steps: ReturnType<typeof purchaseSteps>) {
  const first = firstBuyText(t, steps);
  const later = laterText(t, steps);
  if (plan.total > input.budget) {
    return t('results.decisionHeadlineOver', { amount: formatCurrency(plan.total - input.budget) });
  }
  return t('results.decisionHeadlineFocus', { budget: formatCurrency(input.budget), first, later });
}

function shortBudgetText(t: Translate, plan: FurnishingPlan, input: PlannerInput) {
  const difference = input.budget - plan.total;
  if (difference >= 0) return t('results.budgetRemains', { amount: formatCurrency(difference) });
  return t('results.budgetOver', { amount: formatCurrency(Math.abs(difference)) });
}


function productImage(product: Product) {
  // Sprint 10.23: show the real retailer photo only when it was verified on the live product page;
  // otherwise use a generic category image (clearly flagged as an illustration below).
  return product.imageVerified && product.imageUrl
    ? product.imageUrl
    : FALLBACK_IMAGES[product.category];
}

// Sprint 10.14/10.23: honesty about images. We show the real product photo only when it is verified
// (imageVerified). Otherwise we fall back to a generic category photo and must never imply it is the
// actual product, so we flag it as an illustration (alt text + a discreet chip). We never invent an URL.
function usesFallbackImage(product: Product) {
  return !(product.imageVerified && product.imageUrl);
}

// Sprint 10.14: clearly show the market when a product is from another country's catalog (e.g. SI).
// HR is the home market, so we keep it implicit to avoid noise.
function marketBadge(product: Product) {
  const market = (product.market || '').toUpperCase();
  if (!market || market === 'HR') return '';
  return market;
}

function handleProductImageError(event: SyntheticEvent<HTMLImageElement>, category: ProductCategory) {
  const fallback = FALLBACK_IMAGES[category];
  if (event.currentTarget.src !== fallback) {
    event.currentTarget.src = fallback;
  }
}

function productUrl(product: Product) {
  const url = product.productUrl || product.url || '';
  return url.startsWith('http') ? url : '';
}

// Sprint 10.13 (#2): reviews. We never invent ratings — we only show the verified aggregate the
// catalog recorded (average star + count) and link out to the store so the shopper can read the real
// reviews and judge for themselves (incl. availability). reviewRating is display-only and separate
// from the planner's internal `rating`.
function reviewSummary(t: Translate, product: Product): string {
  const hasRating = typeof product.reviewRating === 'number' && product.reviewRating > 0;
  const hasCount = typeof product.reviewCount === 'number' && product.reviewCount > 0;
  if (hasRating && hasCount) return `★ ${product.reviewRating!.toFixed(1)} (${product.reviewCount})`;
  if (hasRating) return `★ ${product.reviewRating!.toFixed(1)}`;
  if (hasCount) return t('results.reviewCountOnly', { count: product.reviewCount! });
  return '';
}

function hasReviews(t: Translate, product: Product) {
  return reviewSummary(t, product) !== '';
}

function reviewsUrl(product: Product) {
  const url = product.reviewsUrl || product.productUrl || product.url || '';
  return url.startsWith('http') ? url : '';
}

const STALE_AFTER_DAYS = 14;

// Freshness v0: if we last checked this product too long ago (or never), the price and
// availability might have changed, so we nudge the user to confirm in the store.
function isStaleProduct(product: Product) {
  const raw = product.lastCheckedAt;
  if (!raw) return true;
  const parsed = new Date(raw.length >= 10 ? raw.slice(0, 10) : raw);
  if (Number.isNaN(parsed.getTime())) return true;
  const ageDays = (Date.now() - parsed.getTime()) / (1000 * 60 * 60 * 24);
  return ageDays > STALE_AFTER_DAYS;
}

function availabilityLabel(t: Translate, product: Product) {
  const status = product.availabilityStatus || (product.inStock === false ? 'unavailable' : 'in-stock');
  if (status === 'unavailable') return t('results.availUnavailable');
  if (status === 'limited' || status === 'check-store') return t('results.availCheckStore');
  if (status === 'in-stock') return isStaleProduct(product) ? t('results.availCheckStore') : t('results.availInStock');
  return t('results.availCheckStore');
}

function priceTierLabel(t: Translate, product: Product) {
  const tier = product.priceTier;
  if (tier === 'budget') return t('results.priceTierBudget');
  if (tier === 'premium') return t('results.priceTierPremium');
  return t('results.priceTierStandard');
}

function productCheckLabel(t: Translate, product: Product) {
  const status = product.availabilityStatus || (product.inStock === false ? 'unavailable' : 'in-stock');
  if (status === 'limited' || status === 'check-store') return t('results.checkStoreBeforeBuy');
  if (isStaleProduct(product)) return t('results.checkPriceAvailability');
  return t('results.checkPriceInStore');
}

function itemsCountText(t: Translate, count: number) {
  if (count === 1) return t('results.itemCountOne');
  return t('results.itemCountMany', { count });
}

function ShoppingListCard({ plan, input }: { plan: FurnishingPlan; input: PlannerInput }) {
  const { t } = useLocale();
  const trip = resolveStoreTrip(plan);
  const budgetDifference = input.budget - plan.total;
  const budgetText = budgetDifference >= 0
    ? t('results.budgetRemains', { amount: formatCurrency(budgetDifference) })
    : t('results.budgetOverBudget', { amount: formatCurrency(Math.abs(budgetDifference)) });
  const breakdown = getRetailerBreakdown(plan);
  const leadText = trip.storeCount > 0
    ? t('results.shoppingListLead', { stores: storeCountText(trip.storeCount), total: formatCurrency(plan.total) })
    : t('results.shoppingListLeadEmpty');

  return (
    <section className="shopping-list-card" aria-label={t('results.shoppingListTitle')}>
      <div className="shopping-list-head">
        <div>
          <span>{t('results.shoppingListTitle')}</span>
          <strong>{t('results.productsCount', { count: plan.items.length })} · {storeCountText(trip.storeCount)}</strong>
          <small>{budgetText}</small>
        </div>
        <strong>{formatCurrency(plan.total)}</strong>
      </div>
      <p className="shopping-list-lead">{leadText}</p>
      <div className="shopping-list-groups">
        {breakdown.map((entry) => (
          <div className="shopping-list-group" key={entry.retailer}>
            <div className="shopping-list-group-title">
              <span>{entry.retailer} — {itemsCountText(t, entry.count)}</span>
              <strong>{formatCurrency(entry.total)}</strong>
            </div>
            <ul>
              {entry.items.map((item) => (
                <li key={item.product.id}>
                  <span>{item.product.name}</span>
                  {isCheckInStore(item.product)
                    ? <small className="check-store-tag">{t('results.availCheckStore')}</small>
                    : <small>{shoppingPriorityLabels[priorityForItem(item, input.roomType)]}</small>}
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
  const { t } = useLocale();
  return (
    <details className="understood-card compact-understood-card">
      <summary>
        <span>{t('results.consideredTitle')}</span>
        <strong>{formatCurrency(input.budget)} · {roomLabels[input.roomType]} · {styleLabels[input.style]}</strong>
      </summary>
      <div className="understood-grid">
        <div>
          <span>{t('results.fieldRoom')}</span>
          <strong>{roomLabels[input.roomType]}</strong>
        </div>
        <div>
          <span>{t('results.fieldStyle')}</span>
          <strong>{styleLabels[input.style]}</strong>
        </div>
        <div>
          <span>{t('results.fieldSize')}</span>
          <strong>{input.size} m²</strong>
        </div>
        <div>
          <span>{t('results.fieldLevel')}</span>
          <strong>{furnishingLevelLabels[input.furnishingLevel ?? 'comfort']}</strong>
        </div>
        <div>
          <span>{t('results.fieldStores')}</span>
          <strong>{input.retailerMode === 'single' ? t('results.onlyRetailer', { name: input.selectedRetailers[0] }) : input.selectedRetailers.join(' + ')}</strong>
        </div>
      </div>
      <div className="understood-tags">
        <span>{t('results.mustHaveLabel', { categories: labelCategories(t, input.mustHaveCategories) })}</span>
        <span>{t('results.alreadyHaveLabel', { categories: labelCategories(t, input.alreadyHaveCategories) })}</span>
        {input.lockedProductIds.length > 0 && <span>{t('results.keepingLabel', { count: input.lockedProductIds.length })}</span>}
      </div>
    </details>
  );
}

function ResultShell({ children }: { children: ReactNode }) {
  const { t } = useLocale();
  return (
    <div className="results-shell">
      <div className="results-title-bar">
        <div>
          <span className="step-kicker">{t('results.resultKicker')}</span>
          <h3>{t('results.resultHeading')}</h3>
        </div>
        <small>{t('results.resultSubtitle')}</small>
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
  error = null,
  partialNotice = null
}: PlanResultsProps) {
  const { t } = useLocale();
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
            <span>{t('results.errorBadge')}</span>
            <h3>{t('results.errorHeading')}</h3>
            <p>{error}</p>
            <small>{t('results.errorHint')}</small>
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
            <span>{t('results.loadingBadge')}</span>
            <h3>{t('results.loadingHeading')}</h3>
            <p>{t('results.loadingText')}</p>
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
            <span>{t('results.emptyBadge')}</span>
            <h3>{t('results.emptyHeading')}</h3>
            <p>{t('results.emptyText')}</p>
            <div className="empty-example">
              <strong>{t('results.emptyGetLabel')}</strong>
              <span>{t('results.emptyGetItems')}</span>
            </div>
          </div>
        </div>
      </ResultShell>
    );
  }

  const selectedPlan = plans.find((plan) => plan.id === selectedPlanId) ?? plans.find((plan) => plan.name === 'Najbolji izbor') ?? plans[0];
  const overBudget = selectedPlan.total > input.budget;
  const breakdown = getRetailerBreakdown(selectedPlan);
  const trip = resolveStoreTrip(selectedPlan);
  const budgetTight = selectedPlan.total >= input.budget * 0.92;
  const summaryBullets = selectedPlan.purchaseSummary ?? [];
  const repairTips = selectedPlan.budgetRepairSuggestions ?? [];
  const showBudgetBlock = repairTips.length > 0 || budgetTight;
  const missing = missingForRoom(selectedPlan, input);
  const steps = purchaseSteps(selectedPlan, input.roomType);
  const tier = TIER_LABEL_KEYS[selectedPlan.name]
    ? t(TIER_LABEL_KEYS[selectedPlan.name])
    : furnishingLevelLabels[input.furnishingLevel ?? 'comfort'];
  const selectedFeedback = feedbackByPlan[selectedPlan.id];
  const primaryStep = steps.find((step) => step.priority === 'buy-first') ?? steps[0];

  return (
    <ResultShell>
      {partialNotice && (
        <div className="partial-plan-note" role="status">
          <strong>{t('results.partialPlan')}</strong>
          <span>{partialNotice}</span>
        </div>
      )}
      <div className="plans-column decision-results-column">
        <article className="plan-card focused-plan-card decision-plan-card" key={selectedPlan.id}>
          <div className="decision-card">
            <div className="decision-topline">
              <span>{decisionLabel(t, selectedPlan, input)}</span>
              <strong>{selectedPlan.name}</strong>
            </div>
            <h3>{decisionHeadline(t, selectedPlan, input, steps)}</h3>
            {summaryBullets.length > 0 ? (
              <ul className="purchase-summary">
                {summaryBullets.map((line) => (
                  <li key={line}>{line}</li>
                ))}
              </ul>
            ) : (
              <p>{selectedPlan.advisorNote || defaultSummary(t, selectedPlan, input)}</p>
            )}

            <div className="decision-metrics" aria-label={t('results.planKeyInfo')}>
              <div>
                <span>{t('results.totalLabel')}</span>
                <strong>{formatCurrency(selectedPlan.total)}</strong>
              </div>
              <div>
                <span>{overBudget ? t('results.needToReduce') : t('results.safety')}</span>
                <strong className={overBudget ? 'over-text' : ''}>{shortBudgetText(t, selectedPlan, input)}</strong>
              </div>
              <div>
                <span>{t('results.storesLabel')}</span>
                <strong>{selectedPlan.retailersUsed.length || 0}</strong>
              </div>
            </div>

            {showBudgetBlock && (
              <div className="budget-pressure-strip">
                <strong>{t('results.budgetTight')}</strong>
                {repairTips.length > 0 ? (
                  <ul>
                    {repairTips.map((tip) => (
                      <li key={tip}>{tip}</li>
                    ))}
                  </ul>
                ) : (
                  <span>
                    {overBudget
                      ? t('results.budgetTightOver')
                      : t('results.budgetTightUnder')}
                  </span>
                )}
              </div>
            )}

            <div className="store-advice-strip">
              {trip.recommendation}
            </div>

            {selectedPlan.storeLimitNote && (
              <div className="store-limit-note">{selectedPlan.storeLimitNote}</div>
            )}

            {primaryStep && (
              <div className="first-buy-strip">
                <span>{t('results.mostImportantInPlan')}</span>
                <strong>{primaryStep.items.map((item) => item.product.name).join(' + ')}</strong>
              </div>
            )}

            <div className="decision-actions">
              <button className="plan-button primary-copy-button" type="button" onClick={() => copyPlan(selectedPlan)}>
                {copiedPlanId === selectedPlan.id ? t('results.listCopied') : t('results.copyShoppingList')}
              </button>
              <button className="share-button soft" type="button" onClick={() => saveCurrentPlan(selectedPlan, false)} disabled={savingPlanId === selectedPlan.id}>
                {savingPlanId === selectedPlan.id ? t('results.saving') : t('results.saveToMyPlans')}
              </button>
              <button className="share-button soft" type="button" onClick={() => saveCurrentPlan(selectedPlan, true)} disabled={savingPlanId === selectedPlan.id}>
                {t('results.copyLink')}
              </button>
            </div>
          </div>


          <div className="items-list step-items-list">
            <div className="result-section-heading">
              <span>{t('results.productsInPlan')}</span>
              <p>{t('results.productsInPlanHint')}</p>
            </div>
            {steps.map((step) => (
              <section className="step-product-section" key={step.priority}>
                <div className="step-product-section-title">
                  <span>{t(step.titleKey)}</span>
                  <strong>{formatCurrency(step.subtotal)}</strong>
                </div>
                {step.items.map((item) => {
                  const { product, reason } = item;
                  const locked = lockedProductIds.includes(product.id);
                  const priority = priorityForItem(item, input.roomType);
                  const expanded = expandedProductId === product.id;
                  const openUrl = productUrl(product);
                  const reviewsHref = reviewsUrl(product);
                  const market = marketBadge(product);
                  const illustration = usesFallbackImage(product);
                  return (
                    <div className={locked ? 'product-row locked decision-product-row' : 'product-row decision-product-row'} key={product.id}>
                      <img
                        src={productImage(product)}
                        alt={illustration ? t('results.imageIllustrationAlt', { name: product.name }) : product.name}
                        loading="lazy"
                        onError={(event) => handleProductImageError(event, product.category)}
                      />
                      <div className="product-info">
                        <div className="product-title-line">
                          <strong>{product.name}</strong>
                          <span>{formatCurrency(product.price)}</span>
                        </div>
                        <div className="meta-line">
                          <span>{product.retailer}</span>
                          {market && <span title={t('results.marketCatalogTitle', { market })}>{t('results.marketLabel', { market })}</span>}
                          <span>{categoryLabels[product.category]}</span>
                          <span className={`priority-chip ${priority}`}>{shoppingPriorityLabels[priority]}</span>
                          <span>{availabilityLabel(t, product)}</span>
                          {hasReviews(t, product) && (
                            <span className="review-chip" title={t('results.reviewChipTitle')}>
                              {reviewSummary(t, product)}
                            </span>
                          )}
                          {illustration && (
                            <span title={t('results.illustrationTitle')}>
                              {t('results.illustrationChip')}
                            </span>
                          )}
                          {product.originalPrice && <span>{t('results.onSale')}</span>}
                          {locked && <span>{t('results.kept')}</span>}
                        </div>
                        <small className="product-shop-note">{priceTierLabel(t, product)} · {productCheckLabel(t, product)}</small>
                        {product.deliveryNote && <small className="product-delivery-note">{product.deliveryNote}</small>}
                        <div className="product-reason-box compact-reason-box">
                          <span>{item.shoppingRole || t('results.whyThis')}</span>
                          <p>{reason}</p>
                        </div>
                        <div className="product-actions decision-product-actions">
                          {openUrl ? (
                            <a href={openUrl} target="_blank" rel="noopener noreferrer" onClick={() => onProductClick(selectedPlan.id, product)}>
                              {t('results.openInStore')}
                            </a>
                          ) : (
                            <button type="button" disabled title={t('results.productLinkUnavailableTitle')}>
                              {t('results.productLinkUnavailable')}
                            </button>
                          )}
                          {hasReviews(t, product) && reviewsHref && (
                            <a className="reviews-link" href={reviewsHref} target="_blank" rel="noopener noreferrer" onClick={() => onProductClick(selectedPlan.id, product)}>
                              {t('product.reviews')} ↗
                            </a>
                          )}
                          <button type="button" onClick={() => setExpandedProductId(expanded ? null : product.id)} disabled={locked}>
                            {expanded ? t('results.hideReplacements') : t('results.change')}
                          </button>
                          <button type="button" onClick={() => onToggleLock(product.id)}>
                            {locked ? t('results.release') : t('results.keep')}
                          </button>
                        </div>
                        {expanded && (
                          <div className="replacement-menu" aria-label={t('results.replacementMenuLabel')}>
                            <button type="button" onClick={() => onReplace(selectedPlan.id, product.id, 'cheaper')}>{t('results.findCheaper')}</button>
                            <button type="button" onClick={() => onReplace(selectedPlan.id, product.id, 'nicer')}>{t('results.findNicer')}</button>
                            <button type="button" onClick={() => setDislikeProductId(dislikeProductId === product.id ? null : product.id)}>{t('results.dontLikeIt')}</button>
                            <button type="button" onClick={() => onReplace(selectedPlan.id, product.id, 'remove')}>{t('results.dontNeedThis')}</button>
                            {dislikeProductId === product.id && (
                              <div className="dislike-reasons">
                                <span>{t('results.whatsWrong')}</span>
                                <button type="button" onClick={() => onReplace(selectedPlan.id, product.id, 'cheaper')}>{t('results.tooExpensive')}</button>
                                <button type="button" onClick={() => onReplace(selectedPlan.id, product.id, 'nicer')}>{t('results.wantNicerStyle')}</button>
                                <button type="button" onClick={() => onReplace(selectedPlan.id, product.id, 'different')}>{t('results.showAnother')}</button>
                                <button type="button" onClick={() => onReplace(selectedPlan.id, product.id, 'remove')}>{t('results.dontNeedThatItem')}</button>
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

          <ShoppingListCard plan={selectedPlan} input={input} />

          <details className="alternate-plans-panel">
            <summary>
              <span>{t('results.wantDifferent')}</span>
              <strong>{t('results.compareVersions')}</strong>
            </summary>
            <div className="plan-choice-grid compact-plan-choice-grid">
              {plans.map((plan) => {
                const active = plan.id === selectedPlan.id;
                const planOverBudget = plan.total > input.budget;
                return (
                  <button type="button" key={plan.id} className={active ? 'plan-choice-card active' : 'plan-choice-card'} onClick={() => setSelectedPlanId(plan.id)}>
                    <span>{plan.name}</span>
                    <strong>{formatCurrency(plan.total)}</strong>
                    <small>{planOverBudget ? t('results.overBudgetAmount', { amount: formatCurrency(plan.total - input.budget) }) : t('results.remainsAmount', { amount: formatCurrency(input.budget - plan.total) })}</small>
                  </button>
                );
              })}
            </div>
          </details>

          <details className="secondary-info-panel">
            <summary>
              <span>{t('results.planDetails')}</span>
              <strong>{t('results.planDetailsSub')}</strong>
            </summary>

            <div className="plan-summary-box">
              <span>{t('results.whatYouGet')}</span>
              <p>{selectedPlan.summary || defaultSummary(t, selectedPlan, input)}</p>
              <small>{selectedPlan.budgetStatus}</small>
            </div>

            <div className="plan-card-header compact-plan-header">
              <div>
                <span className="plan-label">{selectedPlan.label}</span>
                <h3>{selectedPlan.name}</h3>
                {tier && <small className="plan-tier">{t('results.tierEquipment', { tier })}</small>}
              </div>
              <div className={overBudget ? 'total over' : 'total'}>{formatCurrency(selectedPlan.total)}</div>
            </div>

            <div className="plan-explainer-grid">
              <div>
                <span>{t('results.whoIsThisFor')}</span>
                <p>{selectedPlan.goodFor || t('results.whoIsThisForDefault')}</p>
              </div>
              <div>
                <span>{t('results.whatToWatch')}</span>
                <p>{selectedPlan.tradeoff || selectedPlan.description}</p>
              </div>
            </div>

            <div className="tradeoff-panel" aria-label={t('results.budgetTips')}>
              <div className="tradeoff-card save">
                <span>{t('results.howToLowerPrice')}</span>
                <ul>
                  {(selectedPlan.savingTips?.length ? selectedPlan.savingTips : [t('results.savingTipDefault')]).map((tip) => (
                    <li key={tip}>{tip}</li>
                  ))}
                </ul>
              </div>
              <div className="tradeoff-card upgrade">
                <span>{t('results.ifYouCanAddMore')}</span>
                <ul>
                  {(selectedPlan.upgradeTips?.length ? selectedPlan.upgradeTips : [t('results.upgradeTipDefault')]).map((tip) => (
                    <li key={tip}>{tip}</li>
                  ))}
                </ul>
              </div>
            </div>

            <div className="score-row enhanced-score-row">
              <div>
                <span>{t('results.scoreFit')}</span>
                <strong>{selectedPlan.fitScore}%</strong>
              </div>
              <div>
                <span>{t('results.scoreStyle')}</span>
                <strong>{selectedPlan.styleConsistency}%</strong>
              </div>
              <div>
                <span>{t('results.scoreShopping')}</span>
                <strong>{t(effortLabelKeys[selectedPlan.shoppingEffort])}</strong>
              </div>
              <div>
                <span>{t('results.scoreItemCount')}</span>
                <strong>{selectedPlan.items.length}</strong>
              </div>
            </div>

            <div className="retailer-breakdown">
              <div className="breakdown-title">{t('results.costPerStore')}</div>
              {breakdown.map((entry) => (
                <div className="breakdown-row" key={entry.retailer}>
                  <span>{entry.retailer}</span>
                  <strong>{formatCurrency(entry.total)}</strong>
                  <small>{t('results.productsCount', { count: entry.count })}</small>
                </div>
              ))}
            </div>

            {missing.length > 0 && (
              <div className="missing-box improved-missing-box">
                <strong>{t('results.skippedForNow')}</strong>
                <p>{t('results.skippedForNowText')}</p>
                <ul className="missing-list">
                  {missing.map((category) => (
                    <li key={category}>
                      <span>{categoryLabels[category]}</span>
                      <button type="button" onClick={() => onQuickAction('nicer', selectedPlan)}>{t('results.addToNicerVersion')}</button>
                    </li>
                  ))}
                </ul>
                <small>{t('results.skippedForNowHint')}</small>
              </div>
            )}
          </details>

          <UnderstandingSummary input={input} />

          <div className="feedback-card compact-feedback-card">
            <span>{t('results.isPlanGood')}</span>
            <div className="feedback-buttons">
              {feedbackOptions.map((option) => (
                <button type="button" key={option.value} className={selectedFeedback === option.value ? 'active' : ''} onClick={() => sendFeedback(selectedPlan.id, option.value)}>
                  {t(option.labelKey)}
                </button>
              ))}
            </div>
          </div>
        </article>
      </div>
    </ResultShell>
  );
}
