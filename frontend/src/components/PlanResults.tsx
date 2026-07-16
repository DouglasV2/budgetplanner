import { useEffect, useState, type ReactNode, type SyntheticEvent } from 'react';
import { openPlanPdf } from '../utils/planPdf';
import type {
  CompleteKitchen,
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
import { useAuth } from '../AuthContext';
import { fetchSimilarItems, type SimilarItemsResult } from '../api/client';
import { trackEvent } from '../utils/analytics';
import { marketConfig } from '../markets';

// Sprint 10.102: honest kitchen scope. We plan FREESTANDING kitchen furniture (island/cart, shelves,
// lighting), not fitted/built-in kitchens (the IKEA METOD-style measured system needs a layout planner +
// installation, and has no single honest price). Detect when a prompt is really after a fitted kitchen so we
// can set the expectation and point to IKEA's kitchen planner instead of silently returning loose pieces.
// Diacritics are stripped before matching (same approach as markets.ts), so patterns are plain ASCII.
const FITTED_KITCHEN_PATTERN =
  /(metod|knoxhult|fitted|built[\s-]?in|einbaukuch|ugradben|vgradn|vstavan|inbouw|innebygd|indbygget|po mjeri|po meri|na mieru|su misura|sur mesure|equipee|op maat|a medida|por medida|embutid|etter mal|mattbestall|mittatilaus|componibile|integral|kompletn|komplett|complete|completa)/;

function isFittedKitchenIntent(prompt?: string): boolean {
  if (!prompt) return false;
  const normalized = prompt.toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '');
  return FITTED_KITCHEN_PATTERN.test(normalized);
}

// The fitted-kitchen pointer links to the user's own market IKEA site (where the kitchen planner lives) —
// a market-aware homepage URL that always resolves, never a guessed deep link that could 404.
function ikeaMarketUrl(market?: string): string {
  const config = marketConfig(market);
  return `https://www.ikea.com/${config.code.toLowerCase()}/${config.lang}/`;
}

export type QuickPlanAction = 'cheaper' | 'nicer' | 'single-store' | 'least-stores';

interface PlanResultsProps {
  plans: FurnishingPlan[];
  input: PlannerInput;
  // Sprint 10.183: resolves to whether the plan CHANGED, so a no-op "find nicer/cheaper" can show an honest note.
  onReplace: (planId: string, productId: string, changeType?: ReplacementChoice) => Promise<boolean>;
  onToggleLock: (productId: string) => void;
  lockedProductIds: string[];
  onQuickAction: (action: QuickPlanAction, plan?: FurnishingPlan) => void;
  onSavePlan: (plan: FurnishingPlan, copyLink: boolean) => Promise<string>;
  onProductClick: (planId: string, product: Product) => void;
  // Sprint 10.173 (P0): a user opened one of the "similar under budget" alternatives — centralized analytics
  // (similar_item_click + budget_option_click + product_click) live in Planner, like onProductClick.
  onSimilarProductOpen: (planId: string, product: Product, bucket: string, cap: number) => void;
  onFeedback: (planId: string, feedback: PlanFeedback) => Promise<void>;
  isLoading?: boolean;
  error?: string | null;
  partialNotice?: string | null;
  // Sprint 10.102: the prompt the user actually typed (input.prompt is cleared by the AI response), so the
  // kitchen scope note can detect a fitted-kitchen request.
  submittedPrompt?: string;
  // Sprint 10.51: matched second-hand listings, shown in a separate "Rabljeno" block (never in any total).
  secondHandSuggestions?: Product[];
  // Sprint 10.175: the complete-kitchen section (present only on a complete-kitchen prompt) + its click handler.
  completeKitchen?: CompleteKitchen | null;
  onKitchenSetClick?: (product: Product) => void;
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

// Sprint 10.54: make feedback actionable. Each "this is wrong" maps to the existing quick action that fixes it,
// offered as a one-click CTA after the thank-you — we never auto-regenerate and surprise a user mid-evaluation.
// 'useful' has no fix (it just says thanks). Reuses the proven regeneration path, no new endpoints.
const FEEDBACK_ACTION: Partial<Record<PlanFeedback, { action: QuickPlanAction; labelKey: string }>> = {
  'too-expensive': { action: 'cheaper', labelKey: 'results.feedbackDoCheaper' },
  'wrong-style': { action: 'nicer', labelKey: 'results.feedbackDoNicer' },
  'too-many-stores': { action: 'least-stores', labelKey: 'results.feedbackDoFewerStores' }
};

function labelCategories(t: Translate, categories: ProductCategory[]) {
  if (!categories.length) return t('results.noSpecialTags');
  return categories.map((category) => categoryLabels[category]).join(', ');
}

// Room specific category order. The order follows the way a person usually buys:
// big pieces first, then comfort, then details.
const ROOM_CATEGORY_ORDER: Record<RoomType, ProductCategory[]> = {
  'living-room': ['sofa', 'tv-unit', 'table', 'rug', 'lighting', 'storage', 'textiles', 'decor'],
  'home-office': ['desk', 'chair', 'storage', 'lighting', 'decor'],
  bedroom: ['bed', 'mattress', 'nightstand', 'wardrobe', 'dresser', 'storage', 'lighting', 'textiles', 'decor'],
  'home-gym': ['gym-equipment', 'storage', 'lighting', 'decor'],
  kitchen: ['kitchen-cart', 'kitchen-storage', 'lighting', 'storage', 'decor'],
  'dining-room': ['dining-table', 'dining-chair', 'lighting', 'rug', 'storage', 'decor'],
  hallway: ['storage', 'lighting', 'rug', 'decor'],
  bathroom: ['storage', 'lighting', 'decor'],
  studio: ['bed', 'mattress', 'sofa', 'dining-table', 'wardrobe', 'table', 'storage', 'lighting', 'tv-unit', 'rug', 'nightstand', 'textiles', 'decor']
};

// Sprint 10.155: key the tier label off the STABLE plan id (budget/value/stretch), not the Croatian plan.name.
// The backend's HR names are display strings; matching logic on them silently breaks the tier label the moment a
// name is reworded. id↔name (PlannerService.tierName): value="Najbolji izbor", budget="Najjeftinije",
// stretch="Ljepša verzija".
const TIER_LABEL_KEYS: Record<string, string> = {
  budget: 'results.tierBasic',
  value: 'results.tierComfort',
  stretch: 'results.tierComplete'
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
  bathroom: ['toilet', 'washbasin', 'bath-shower'],
  studio: ['bed', 'mattress', 'sofa']
};

const FALLBACK_IMAGES: Record<ProductCategory, string> = {
  sofa: 'https://images.unsplash.com/photo-1555041469-a586c61ea9bc?auto=format&fit=crop&w=240&q=70',
  chair: 'https://images.unsplash.com/photo-1589384267710-7a25bc5ca5f3?auto=format&fit=crop&w=240&q=70',
  table: 'https://images.unsplash.com/photo-1532372320572-cda25653a694?auto=format&fit=crop&w=240&q=70',
  'tv-unit': 'https://images.unsplash.com/photo-1615873968403-89e068629265?auto=format&fit=crop&w=240&q=70',
  storage: 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=240&q=70',
  rug: 'https://images.unsplash.com/photo-1600166898405-da9535204843?auto=format&fit=crop&w=240&q=70',
  lighting: 'https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=240&q=70',
  decor: 'https://images.unsplash.com/photo-1513519245088-0e12902e5a38?auto=format&fit=crop&w=240&q=70',
  desk: 'https://images.unsplash.com/photo-1518455027359-f3f8164ba6bd?auto=format&fit=crop&w=240&q=70',
  bed: 'https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=240&q=70',
  mattress: 'https://images.unsplash.com/photo-1631049307264-da0ec9d70304?auto=format&fit=crop&w=240&q=70',
  'gym-equipment': 'https://images.unsplash.com/photo-1583454110551-21f2fa2afe61?auto=format&fit=crop&w=240&q=70',
  // New rooms (Sprint 10.7): reuse the closest existing category placeholder image.
  'dining-table': 'https://images.unsplash.com/photo-1532372320572-cda25653a694?auto=format&fit=crop&w=240&q=70',
  'dining-chair': 'https://images.unsplash.com/photo-1589384267710-7a25bc5ca5f3?auto=format&fit=crop&w=240&q=70',
  'kitchen-storage': 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=240&q=70',
  'kitchen-cart': 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=240&q=70',
  // Sprint 10.175: modular kitchen set — a neutral kitchen photo (real sets are imageVerified, so this rarely shows).
  'kitchen-set': 'https://images.unsplash.com/photo-1556909212-d5b604d0c90d?auto=format&fit=crop&w=240&q=70',
  // Sprint 10.176: kitchen appliances (real IKEA appliances are imageVerified, so these fallbacks rarely show).
  oven: 'https://images.unsplash.com/photo-1585659722983-3a675dabf23d?auto=format&fit=crop&w=240&q=70',
  hob: 'https://images.unsplash.com/photo-1556910633-5099dc3971e8?auto=format&fit=crop&w=240&q=70',
  'cooker-hood': 'https://images.unsplash.com/photo-1556909212-d5b604d0c90d?auto=format&fit=crop&w=240&q=70',
  fridge: 'https://images.unsplash.com/photo-1571175443880-49e1d25b2bc5?auto=format&fit=crop&w=240&q=70',
  freezer: 'https://images.unsplash.com/photo-1571175443880-49e1d25b2bc5?auto=format&fit=crop&w=240&q=70',
  dishwasher: 'https://images.unsplash.com/photo-1585659722983-3a675dabf23d?auto=format&fit=crop&w=240&q=70',
  microwave: 'https://images.unsplash.com/photo-1585659722983-3a675dabf23d?auto=format&fit=crop&w=240&q=70',
  nightstand: 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=240&q=70',
  wardrobe: 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=240&q=70',
  dresser: 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=240&q=70',
  // Soft furnishings — reuse the rug placeholder (textiles are imageVerified, so this fallback rarely shows).
  textiles: 'https://images.unsplash.com/photo-1600166898405-da9535204843?auto=format&fit=crop&w=240&q=70',
  // Sprint 10.169: bathroom fixtures — reuse a neutral placeholder (Pevex fixtures are imageVerified, so this
  // fallback rarely shows).
  toilet: 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=240&q=70',
  washbasin: 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=240&q=70',
  'bath-shower': 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=240&q=70'
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

// Sprint 10.112: localized narrative for non-HR locales. The backend's rich plan narrative (advisorNote,
// budgetStatus, reasons, roles, …) is Croatian-only; for any other language we build clean localized text
// from i18n + plan data so a non-HR user never sees Croatian mixed into a localized UI.
function localBudgetStatus(t: Translate, plan: FurnishingPlan, input: PlannerInput) {
  const diff = input.budget - plan.total;
  if (diff < 0) return t('results.budgetOverBy', { amount: formatCurrency(-diff) });
  if (plan.total <= input.budget * 0.85) return t('results.budgetComfortable');
  return t('results.budgetFits');
}

function preferredPlanId(plans: FurnishingPlan[], input: PlannerInput) {
  if (!plans.length) return null;
  const preferredId = input.optimizationGoal === 'lowest-price' || input.furnishingLevel === 'basic'
    ? 'budget'
    : input.optimizationGoal === 'style-match' || input.furnishingLevel === 'complete'
    ? 'stretch'
    : 'value';
  return plans.find((plan) => plan.id === preferredId)?.id ?? plans.find((plan) => plan.id === 'value')?.id ?? plans[0].id;
}

// Sprint 10.171: shortBudgetText removed — the report grid's Budžet card shows the remaining amount directly.


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

interface SaleInfo {
  percent: number;
  amount: number;
  original: number;
  endsAt?: string;
}

// Sprint 10.33: a verified sale. A product counts as "on sale" only when it carries a verified
// originalPrice (the regular price) strictly above the current price — the % and € saving are both
// derived from those verified numbers, never fabricated. If a saleEndsAt window exists and has already
// passed, we deliberately do NOT show the discount (an expired promo would be a false claim); the normal
// "provjeri cijenu u trgovini" freshness caveat then takes over.
function saleInfo(product: Product): SaleInfo | null {
  const original = product.originalPrice;
  if (typeof original !== 'number' || !(original > product.price)) return null;
  if (product.saleEndsAt && saleWindowEnded(product.saleEndsAt)) return null;
  const amount = original - product.price;
  const percent = Math.round((amount / original) * 100);
  if (percent <= 0) return null;
  return { percent, amount, original, endsAt: product.saleEndsAt };
}

function saleWindowEnded(saleEndsAt: string): boolean {
  const parsed = new Date(saleEndsAt.length >= 10 ? saleEndsAt.slice(0, 10) : saleEndsAt);
  if (Number.isNaN(parsed.getTime())) return false; // unparseable → don't suppress on that basis
  parsed.setHours(23, 59, 59, 999);
  return Date.now() > parsed.getTime();
}

function formatSaleEndDate(saleEndsAt: string): string {
  const parsed = new Date(saleEndsAt.length >= 10 ? saleEndsAt.slice(0, 10) : saleEndsAt);
  if (Number.isNaN(parsed.getTime())) return '';
  const dd = String(parsed.getDate()).padStart(2, '0');
  const mm = String(parsed.getMonth() + 1).padStart(2, '0');
  return `${dd}.${mm}.${parsed.getFullYear()}`;
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

// Sprint 10.59: budget breakdown — makes the planner's existing budget allocation VISIBLE. A single stacked
// bar shows where the money goes (share per category) and the remaining budget is highlighted. Plan data only.
const BUDGET_COLORS = ['#253746', '#6F7A63', '#A96849', '#8C7A66', '#B0894E', '#5E7488', '#9C6B4A'];

function BudgetBreakdown({ plan, input }: { plan: FurnishingPlan; input: PlannerInput }) {
  const { t } = useLocale();
  const total = plan.total;
  if (!plan.items.length || total <= 0) return null;

  const byCategory = new Map<ProductCategory, number>();
  plan.items.forEach((item) => {
    byCategory.set(item.product.category, (byCategory.get(item.product.category) ?? 0) + item.product.price);
  });
  const segments = Array.from(byCategory.entries())
    .map(([category, amount]) => ({ category, amount, pct: Math.round((amount / total) * 100) }))
    .sort((a, b) => b.amount - a.amount);
  const remaining = input.budget - total;

  return (
    <section className="budget-breakdown-card" aria-label={t('results.budgetBreakdownTitle')}>
      <div className="budget-breakdown-head">
        <span>{t('results.budgetBreakdownTitle')}</span>
        <strong className={remaining < 0 ? 'over-text' : 'left-text'}>
          {remaining >= 0
            ? t('results.budgetLeftShort', { amount: formatCurrency(remaining) })
            : t('results.budgetOverShort', { amount: formatCurrency(Math.abs(remaining)) })}
        </strong>
      </div>
      <div className="budget-bar" role="img" aria-label={t('results.budgetBreakdownTitle')}>
        {segments.map((segment, index) => (
          <span
            key={segment.category}
            className="budget-bar-seg"
            style={{ width: `${(segment.amount / total) * 100}%`, background: BUDGET_COLORS[index % BUDGET_COLORS.length] }}
            title={`${categoryLabels[segment.category]} · ${formatCurrency(segment.amount)} · ${segment.pct}%`}
          />
        ))}
      </div>
      <ul className="budget-legend">
        {segments.slice(0, 6).map((segment, index) => (
          <li key={segment.category}>
            <span className="budget-dot" style={{ background: BUDGET_COLORS[index % BUDGET_COLORS.length] }} aria-hidden="true" />
            <span className="budget-cat">{categoryLabels[segment.category]}</span>
            <strong>{formatCurrency(segment.amount)}</strong>
            <small>{segment.pct}%</small>
          </li>
        ))}
      </ul>
    </section>
  );
}

// Sprint 10.60: social share — an organic growth loop. A clean summary card of the plan ("My living room for
// £1500 — sofa £349… total £827, £673 left. Built with BudgetSpace.") that the user can send to a friend or
// post (WhatsApp / Reddit / X / native share / copy). Reuses onSavePlan to mint a shareable /plan/<id> link.
// Sprint 10.70: the "Built with BudgetSpace" watermark is a Free perk of sharing; paid plans (Plus/Pro) share
// clean. The footer also doubles as the organic-growth tag, so only paid users may drop it.
function buildShareText(t: Translate, plan: FurnishingPlan, input: PlannerInput, withWatermark: boolean): string {
  const room = roomLabels[input.roomType];
  const items = plan.items
    .slice(0, 3)
    .map((item) => `${categoryLabels[item.product.category]} ${formatCurrency(item.product.price)}`)
    .join(', ');
  const remaining = input.budget - plan.total;
  const lead = t('results.shareLead', { room, budget: formatCurrency(input.budget) });
  const totalPart = t('results.shareTotal', { total: formatCurrency(plan.total) });
  const savedPart = remaining >= 0 ? ` · ${t('results.shareSaved', { amount: formatCurrency(remaining) })}` : '';
  return `${lead} ${items}. ${totalPart}${savedPart}.${withWatermark ? ` ${t('results.shareFooter')}` : ''}`;
}

function SharePanel({ plan, input, onSavePlan }: {
  plan: FurnishingPlan;
  input: PlannerInput;
  onSavePlan: (plan: FurnishingPlan, copyLink: boolean) => Promise<string>;
}) {
  const { t } = useLocale();
  const { user } = useAuth();
  const paid = user?.plan === 'PLUS' || user?.plan === 'PRO';
  const [url, setUrl] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  const summary = buildShareText(t, plan, input, !paid);
  const canNativeShare = typeof navigator !== 'undefined' && typeof (navigator as Navigator & { share?: unknown }).share === 'function';

  // Mint (once) a shareable /plan/<id> link by saving the plan; cache it so repeated shares don't re-save.
  async function ensureUrl(): Promise<string> {
    if (url) return url;
    setBusy(true);
    try {
      const saved = await onSavePlan(plan, false);
      // Sprint 10.68: onSavePlan returns '' when the save was blocked (e.g. Free saved-plan limit) — don't cache
      // an empty link, so a later retry (after upgrading) can mint one.
      if (saved) setUrl(saved);
      return saved;
    } finally {
      setBusy(false);
    }
  }

  async function shareTo(target: 'whatsapp' | 'reddit' | 'x') {
    const link = await ensureUrl();
    if (!link) return; // save was blocked (e.g. Plus limit) — the upsell notice is already shown
    const enc = encodeURIComponent;
    const href =
      target === 'whatsapp' ? `https://wa.me/?text=${enc(`${summary} ${link}`)}`
      : target === 'reddit' ? `https://www.reddit.com/submit?url=${enc(link)}&title=${enc(summary)}`
      : `https://twitter.com/intent/tweet?text=${enc(summary)}&url=${enc(link)}`;
    window.open(href, '_blank', 'noopener,noreferrer');
  }

  async function nativeShare() {
    const link = await ensureUrl();
    if (!link) return;
    const nav = navigator as Navigator & { share?: (data: { title?: string; text?: string; url?: string }) => Promise<void> };
    try {
      await nav.share?.({ title: t('results.shareTitle'), text: summary, url: link });
    } catch {
      // user cancelled or unsupported — no-op
    }
  }

  async function copyShare() {
    const link = await ensureUrl();
    if (!link) return;
    try {
      await navigator.clipboard.writeText(`${summary} ${link}`);
    } catch {
      // clipboard blocked — the text is still visible in the preview
    }
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1800);
  }

  return (
    <section className="share-card" aria-label={t('results.shareTitle')}>
      <div className="share-head">
        <span>{t('results.shareTitle')}</span>
        <small>{t('results.shareHint')}</small>
      </div>
      <p className="share-preview">{summary}</p>
      <div className="share-actions">
        {canNativeShare && (
          <button type="button" className="share-btn primary" onClick={nativeShare} disabled={busy}>{t('results.shareNative')}</button>
        )}
        <button type="button" className="share-btn whatsapp" onClick={() => shareTo('whatsapp')} disabled={busy}>WhatsApp</button>
        <button type="button" className="share-btn reddit" onClick={() => shareTo('reddit')} disabled={busy}>Reddit</button>
        <button type="button" className="share-btn x" onClick={() => shareTo('x')} disabled={busy}>X</button>
        <button type="button" className="share-btn" onClick={copyShare} disabled={busy}>{copied ? t('results.shareCopied') : t('results.shareCopy')}</button>
      </div>
    </section>
  );
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
              {entry.items.map((item) => {
                const q = item.quantity && item.quantity > 1 ? item.quantity : 1;
                return (
                <li key={item.product.id}>
                  <span>{q > 1 ? `${q} × ${item.product.name}` : item.product.name}</span>
                  {isCheckInStore(item.product)
                    ? <small className="check-store-tag">{t('results.availCheckStore')}</small>
                    : <small>{shoppingPriorityLabels[priorityForItem(item, input.roomType)]}</small>}
                  <strong>{formatCurrency(item.product.price * q)}</strong>
                </li>
                );
              })}
            </ul>
          </div>
        ))}
      </div>
    </section>
  );
}

// Sprint 10.153: the second-hand ("Rabljeno") block is HIDDEN. The eBay matching is title-derived (so it
// mis-tags pieces, e.g. a TV-stand bracket as a "coffee table") and the cards can only show generic category
// illustrations (eBay photos aren't verified), so the section confused more than it helped. Flip this to true to
// bring it back once there's a better used-furniture source + real photos. (The component is kept intact.)
const SHOW_SECOND_HAND = false;

// Sprint 10.51: the separate "Rabljeno" (second-hand) block. Used marketplace listings (e.g. eBay) shown as
// an optional, clearly-labelled alternative under the new-retail plan — NEVER counted into any plan total.
// Honest by design: a "≈" asking price (negotiable), the seller's stated condition, the city, a buyer-beware
// disclaimer, and a link straight to the live listing (availability is the seller's to confirm, not ours).
function SecondHandSection({
  products,
  planId,
  onProductClick
}: {
  products: Product[];
  planId: string;
  onProductClick: (planId: string, product: Product) => void;
}) {
  const { t } = useLocale();
  if (!products.length) return null;
  return (
    <section className="second-hand-section" aria-label={t('results.secondHandTitle')}>
      <div className="second-hand-head">
        <span className="second-hand-kicker">{t('results.secondHandKicker')}</span>
        <strong>{t('results.secondHandTitle')}</strong>
        <small>{t('results.secondHandSubtitle')}</small>
      </div>
      <p className="second-hand-disclaimer">{t('results.secondHandDisclaimer')}</p>
      <div className="second-hand-grid">
        {products.map((product) => {
          const openUrl = productUrl(product);
          const market = marketBadge(product);
          const condition = (product.conditionLabel ?? '').trim();
          const location = (product.sellerLocation ?? '').trim();
          return (
            <article className="second-hand-card" key={product.id}>
              <img
                src={productImage(product)}
                alt={t('results.imageIllustrationAlt', { name: product.name })}
                loading="lazy"
                onError={(event) => handleProductImageError(event, product.category)}
              />
              <div className="second-hand-info">
                <strong className="second-hand-name">{product.name}</strong>
                <div className="meta-line">
                  <span>{product.retailer}</span>
                  {market && <span title={t('results.marketCatalogTitle', { market })}>{t('results.marketLabel', { market })}</span>}
                  <span>{categoryLabels[product.category]}</span>
                  {condition && <span className="condition-chip" title={t('results.secondHandConditionTitle')}>{condition}</span>}
                </div>
                {location && <small className="second-hand-location">{t('results.secondHandLocation', { location })}</small>}
                <div className="second-hand-price">
                  <strong>{t('results.secondHandApproxPrice', { price: formatCurrency(product.price) })}</strong>
                  <small>{t('results.secondHandPriceNote')}</small>
                </div>
                <div className="product-actions second-hand-actions">
                  {openUrl ? (
                    <a href={openUrl} target="_blank" rel="noopener noreferrer" onClick={() => onProductClick(planId, product)}>
                      {t('results.secondHandOpenListing')}
                    </a>
                  ) : (
                    <button type="button" disabled title={t('results.productLinkUnavailableTitle')}>{t('results.productLinkUnavailable')}</button>
                  )}
                </div>
              </div>
            </article>
          );
        })}
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

// Sprint 10.171: small line icons for the plan report cards (budget / products / stores / priorities).
function CardIcon({ name }: { name: 'budget' | 'products' | 'stores' | 'priorities' | 'list' }) {
  const glyph: Record<string, ReactNode> = {
    budget: <><rect x="3" y="6" width="18" height="12" rx="2" /><path d="M3 10.5h18" /><circle cx="16.5" cy="14" r="1.1" /></>,
    products: <><path d="M4.5 8h15l-1 11.5h-13z" /><path d="M9 8a3 3 0 0 1 6 0" /></>,
    stores: <><path d="M4 4h2l1.8 11h9.4l1.8-8H7" /><circle cx="9.5" cy="19" r="1.3" /><circle cx="16.5" cy="19" r="1.3" /></>,
    priorities: <path d="M12 3.5l2.5 5.3 5.8.7-4.3 4 1.1 5.7L12 16.4 6.9 19.2 8 13.5 3.7 9.5l5.8-.7z" />,
    list: <><path d="M9 6h11" /><path d="M9 12h11" /><path d="M9 18h11" /><circle cx="4.5" cy="6" r="1.1" /><circle cx="4.5" cy="12" r="1.1" /><circle cx="4.5" cy="18" r="1.1" /></>,
  };
  return (
    <svg className="report-card-glyph" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {glyph[name]}
    </svg>
  );
}

function ResultShell({ children }: { children: ReactNode }) {
  // Sprint 10.168: the separate REZULTAT title-bar was removed — the header now lives inside the plan card's
  // topline (with a "?" for the price caveat), so the card sits higher and there's no redundant mini-card.
  return (
    <div className="results-shell">
      {children}
    </div>
  );
}

// Sprint 10.173 (P0 — similar-item + budget-option discovery): a per-product "browse & open" panel. Given the
// anchor row and a budget cap, it fetches up to three verified in-catalog alternatives (budget pick / best value
// / nicer) and shows them as cards the user can open in the store. It NEVER mutates the plan (that's the separate
// "Change" flow) and never fabricates — a bucket with no product just isn't shown.
type SimilarBucket = 'budget' | 'value' | 'nicer';

const SIMILAR_BUCKETS: Array<{
  key: SimilarBucket;
  field: 'budgetPick' | 'bestValue' | 'nicer';
  titleKey: string;
  whyKey: string;
}> = [
  { key: 'budget', field: 'budgetPick', titleKey: 'similar.budgetPick', whyKey: 'similar.budgetWhy' },
  { key: 'value', field: 'bestValue', titleKey: 'similar.bestValue', whyKey: 'similar.valueWhy' },
  { key: 'nicer', field: 'nicer', titleKey: 'similar.nicer', whyKey: 'similar.nicerWhy' }
];

// Budget-cap chips per currency. EUR/GBP use the spec's €50/€100/€150; higher-denomination currencies use
// proportionate round numbers so a NOK/HUF user sees sensible caps instead of a literal "€50". (The 4th chip,
// "under my remaining budget", is computed from the plan, not this table.)
const CAP_TIERS_BY_CURRENCY: Record<string, [number, number, number]> = {
  EUR: [50, 100, 150],
  GBP: [50, 100, 150],
  NOK: [500, 1000, 1500],
  SEK: [500, 1000, 1500],
  DKK: [350, 750, 1100],
  CZK: [1200, 2500, 3800],
  HUF: [20000, 40000, 60000],
  PLN: [220, 450, 650],
  RON: [250, 500, 750]
};

function SimilarItemsPanel({ anchor, input, remainingBudget, onOpenProduct }: {
  anchor: Product;
  input: PlannerInput;
  remainingBudget: number;
  onOpenProduct: (product: Product, bucket: SimilarBucket, cap: number) => void;
}) {
  const { t } = useLocale();
  const market = input.market;
  const currency = marketConfig(market).currency;
  const tiers = CAP_TIERS_BY_CURRENCY[currency] ?? CAP_TIERS_BY_CURRENCY.EUR;
  // "Under my remaining budget" is the differentiator over the fixed caps; offer it only when there's meaningful
  // room left (≥ the smallest tier), otherwise the largest fixed tier is the sensible default.
  const remainingCap = remainingBudget > 0 ? Math.round(remainingBudget) : 0;
  const showRemaining = remainingCap >= tiers[0];
  const [cap, setCap] = useState<number>(showRemaining ? remainingCap : tiers[2]);
  const [data, setData] = useState<SimilarItemsResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  // Fire the "panel opened" event once when the panel mounts for a product.
  useEffect(() => {
    trackEvent('similar_items_open', { category: anchor.category, retailer: anchor.retailer, market: market ?? 'HR' });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // (Re)fetch whenever the cap changes — including the initial default cap on mount.
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);
    fetchSimilarItems(anchor, input, cap)
      .then((res) => { if (!cancelled) setData(res); })
      .catch(() => { if (!cancelled) setError(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cap, anchor.id]);

  function selectCap(next: number) {
    if (next === cap) return;
    trackEvent('budget_compare_open', { cap: next, category: anchor.category });
    setCap(next);
  }

  const hasAnyCard = !!(data && (data.budgetPick || data.bestValue || data.nicer));

  return (
    <div className="similar-panel" role="region" aria-label={t('similar.title')}>
      <div className="similar-head">
        <strong>{t('similar.title')}</strong>
        <small>{t('similar.subtitle', { name: anchor.name })}</small>
      </div>
      <div className="similar-caps" role="group" aria-label={t('similar.title')}>
        {tiers.map((tier) => (
          <button
            key={tier}
            type="button"
            className={cap === tier ? 'similar-cap active' : 'similar-cap'}
            aria-pressed={cap === tier}
            onClick={() => selectCap(tier)}
          >
            {t('similar.capUnder', { amount: formatCurrency(tier, market) })}
          </button>
        ))}
        {showRemaining && (
          <button
            type="button"
            className={cap === remainingCap ? 'similar-cap active' : 'similar-cap'}
            aria-pressed={cap === remainingCap}
            onClick={() => selectCap(remainingCap)}
          >
            {t('similar.capRemaining', { amount: formatCurrency(remainingCap, market) })}
          </button>
        )}
      </div>

      {loading && <p className="similar-status" role="status">{t('similar.loading')}</p>}
      {error && !loading && <p className="similar-status similar-error" role="status">{t('similar.error')}</p>}
      {!loading && !error && data && !hasAnyCard && (
        <p className="similar-status" role="status">{t('similar.empty', { amount: formatCurrency(cap, market) })}</p>
      )}
      {!loading && !error && data && hasAnyCard && (
        <div className="similar-grid">
          {SIMILAR_BUCKETS.map((bucket) => {
            const product = data[bucket.field];
            if (!product) return null;
            const openUrl = productUrl(product);
            const badge = marketBadge(product);
            const sale = saleInfo(product);
            const illustration = usesFallbackImage(product);
            return (
              <article className={`similar-card similar-card-${bucket.key}`} key={bucket.key}>
                <span className={`similar-tag similar-tag-${bucket.key}`}>{t(bucket.titleKey)}</span>
                <img
                  src={productImage(product)}
                  alt={illustration ? t('results.imageIllustrationAlt', { name: product.name }) : product.name}
                  loading="lazy"
                  onError={(event) => handleProductImageError(event, product.category)}
                />
                <strong className="similar-name">{product.name}</strong>
                <div className="similar-price">
                  <strong>{formatCurrency(product.price, market)}</strong>
                  {sale && (
                    <s className="original-price" title={t('results.regularPrice', { price: formatCurrency(sale.original, market) })}>
                      {formatCurrency(sale.original, market)}
                    </s>
                  )}
                </div>
                <div className="meta-line similar-meta">
                  <span>{product.retailer}</span>
                  {badge && <span title={t('results.marketCatalogTitle', { market: badge })}>{t('results.marketLabel', { market: badge })}</span>}
                  {sale && <span className="sale-chip">{t('results.onSale')}</span>}
                  <span>{availabilityLabel(t, product)}</span>
                </div>
                <small className="similar-why">{t(bucket.whyKey)}</small>
                {openUrl ? (
                  <a
                    className="similar-open"
                    href={openUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    onClick={() => onOpenProduct(product, bucket.key, cap)}
                  >
                    {t('similar.openProduct')} ↗
                  </a>
                ) : (
                  <button type="button" className="similar-open" disabled title={t('results.productLinkUnavailableTitle')}>
                    {t('results.productLinkUnavailable')}
                  </button>
                )}
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}

// Sprint 10.175 (kitchen Increment 1): the inline "Kompletna kuhinja" section. Shows real modular kitchen sets
// (each a Product, category kitchen-set) for a complete-kitchen prompt, with an honest "modular, not fitted"
// note + the IKEA-planner link for true made-to-measure kitchens. Browse-only; never mutates the plan. Empty
// sets => an honest "no set fits" message (the note still shows).
const KITCHEN_SHAPE_KEYS: Record<string, string> = {
  'single-wall': 'kitchen.shapeSingleWall',
  'l-shaped': 'kitchen.shapeLShaped',
  'u-shaped': 'kitchen.shapeUShaped',
  galley: 'kitchen.shapeGalley',
  island: 'kitchen.shapeIsland'
};

function CompleteKitchenSection({ completeKitchen, input, onSetClick }: {
  completeKitchen: CompleteKitchen;
  input: PlannerInput;
  onSetClick: (product: Product) => void;
}) {
  const { t } = useLocale();
  const market = input.market;
  const shapeKey = KITCHEN_SHAPE_KEYS[completeKitchen.shape];
  const understoodParts: string[] = [];
  if (shapeKey) understoodParts.push(t(shapeKey));
  if (completeKitchen.includeAppliances) understoodParts.push(t('kitchen.withAppliances'));
  const understood = understoodParts.join(' · ');

  return (
    <section className="complete-kitchen" aria-label={t('kitchen.completeTitle')}>
      <div className="complete-kitchen-head">
        <strong>{t('kitchen.completeTitle')}</strong>
        <small>{t('kitchen.completeSubtitle')}</small>
      </div>
      {understood && <p className="complete-kitchen-understood">{t('kitchen.understood', { details: understood })}</p>}
      <p className="kitchen-modular-note" role="note">{t('kitchen.modularNote')}</p>
      {completeKitchen.sets.length === 0 ? (
        <p className="complete-kitchen-empty">{t('kitchen.emptySets', { budget: formatCurrency(input.budget, market) })}</p>
      ) : (
        <div className="complete-kitchen-grid">
          {completeKitchen.sets.map((set) => {
            const openUrl = productUrl(set);
            const badge = marketBadge(set);
            const illustration = usesFallbackImage(set);
            return (
              <article className="kitchen-set-card" key={set.id}>
                <img
                  src={productImage(set)}
                  alt={illustration ? t('results.imageIllustrationAlt', { name: set.name }) : set.name}
                  loading="lazy"
                  onError={(event) => handleProductImageError(event, set.category)}
                />
                <strong className="kitchen-set-name">{set.name}</strong>
                <div className="meta-line kitchen-set-meta">
                  <span>{set.retailer}</span>
                  {badge && <span title={t('results.marketCatalogTitle', { market: badge })}>{t('results.marketLabel', { market: badge })}</span>}
                  <strong>{formatCurrency(set.price, market)}</strong>
                </div>
                {openUrl ? (
                  <a className="similar-open" href={openUrl} target="_blank" rel="noopener noreferrer" onClick={() => onSetClick(set)}>
                    {t('kitchen.openSet')} ↗
                  </a>
                ) : (
                  <button type="button" className="similar-open" disabled title={t('results.productLinkUnavailableTitle')}>
                    {t('results.productLinkUnavailable')}
                  </button>
                )}
              </article>
            );
          })}
        </div>
      )}
      <a className="kitchen-planner-link" href={ikeaMarketUrl(market)} target="_blank" rel="noopener noreferrer">
        {t('kitchen.plannerLink')} ↗
      </a>
    </section>
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
  onSimilarProductOpen,
  onFeedback,
  isLoading = false,
  error = null,
  partialNotice = null,
  submittedPrompt = '',
  secondHandSuggestions = [],
  completeKitchen = null,
  onKitchenSetClick
}: PlanResultsProps) {
  const { t, config, lang } = useLocale();
  // Sprint 10.112: for any non-Croatian locale the backend's Croatian narrative is replaced by localized text.
  // Sprint 10.152: keyed off the EFFECTIVE language so the "read in English" override also rebuilds the narrative
  // (an HR user who switches to English gets English text, not the backend's Croatian).
  const localize = lang !== 'hr';
  const [copiedPlanId, setCopiedPlanId] = useState<string | null>(null);
  const [savingPlanId, setSavingPlanId] = useState<string | null>(null);
  // Sprint 10.168: the price/availability caveat now lives behind the "?" next to the result title.
  const [showPriceInfo, setShowPriceInfo] = useState(false);
  const [feedbackByPlan, setFeedbackByPlan] = useState<Record<string, PlanFeedback>>({});
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);
  const [expandedProductId, setExpandedProductId] = useState<string | null>(null);
  const [dislikeProductId, setDislikeProductId] = useState<string | null>(null);
  // Sprint 10.62: per-product secondary actions (change / keep / replace) hide behind one toggle so a row
  // shows just the price, the reason and "open in store" by default. Only one row's actions open at a time.
  const [actionsProductId, setActionsProductId] = useState<string | null>(null);
  // Sprint 10.173 (P0): the row whose "Slično ispod budžeta" discovery panel is open (one at a time).
  const [similarProductId, setSimilarProductId] = useState<string | null>(null);
  // Sprint 10.183: the row where the last replace found nothing (so we show an honest inline note there), plus the
  // matching message key ("no nicer/cheaper/other option"). Cleared on any new swap attempt and on a new plan set.
  const [noSwapProductId, setNoSwapProductId] = useState<string | null>(null);
  const [noSwapMessageKey, setNoSwapMessageKey] = useState<string>('results.noNicerFound');

  // Sprint 10.62: a genuinely new plan SET (fresh generation / opened plan) is identified by its plan ids; an
  // in-place product replace keeps the same ids. We key the per-row disclosure reset on the ids so the
  // "More options" row a user is editing survives an iterative replace instead of snapping shut under them.
  const planIdsKey = plans.map((plan) => plan.id).join('|');

  // Sprint 10.168: keep the user's MANUAL version choice across an in-place replace (which returns a new plans
  // array with the same ids). Only re-derive the preferred plan when the plan SET actually changes — otherwise
  // every "find cheaper/nicer/remove" snapped the view back to the deterministic preferred plan.
  useEffect(() => {
    setSelectedPlanId((prev) => (plans.some((plan) => plan.id === prev) ? prev : preferredPlanId(plans, input)));
  }, [plans, input.optimizationGoal, input.furnishingLevel]);

  // Clear stale feedback only on a genuinely new plan set (Sprint 10.54) — NOT on an in-place replace or a
  // version switch, so a just-given "thanks" / actionable CTA isn't wiped the instant the user tweaks a product.
  useEffect(() => {
    setFeedbackByPlan({});
  }, [planIdsKey, input.optimizationGoal, input.furnishingLevel]);

  // Reset per-row disclosure state on a new plan set OR when switching between compared versions (Sprint 10.168:
  // selectedPlanId added) — these panels are keyed by product.id only, so without this an open More-options /
  // replace panel leaks onto a different plan's row that happens to share the same product id. An in-place
  // replace keeps planIdsKey + selectedPlanId, so the row a user is editing still survives the swap.
  useEffect(() => {
    setExpandedProductId(null);
    setDislikeProductId(null);
    setActionsProductId(null);
    setSimilarProductId(null);
    setNoSwapProductId(null);
  }, [planIdsKey, selectedPlanId, input.optimizationGoal, input.furnishingLevel]);

  // Sprint 10.183: run a replace and, when it finds nothing (the plan comes back UNCHANGED), show an honest inline
  // note on that row instead of the button appearing dead. 'remove' always changes the plan, so it never gets here.
  async function requestReplace(planId: string, productId: string, changeType: ReplacementChoice) {
    setNoSwapProductId(null);
    const changed = await onReplace(planId, productId, changeType);
    if (changed === false) {
      setNoSwapMessageKey(
        changeType === 'cheaper' ? 'results.noCheaperFound'
          : changeType === 'nicer' ? 'results.noNicerFound'
            : 'results.noOtherFound'
      );
      setNoSwapProductId(productId);
    }
  }

  // Sprint 10.62: open/close a row's secondary actions. Toggling always collapses that row's nested menus so a
  // fresh open starts clean and a closed row never leaves an orphaned replacement panel showing.
  function toggleActions(productId: string) {
    setActionsProductId((current) => (current === productId ? null : productId));
    setExpandedProductId(null);
    setDislikeProductId(null);
  }

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

  // Sprint 10.175: the complete-kitchen section renders whenever a complete-kitchen prompt returned one — even
  // if the freestanding plan is empty — so it lives outside the plan-specific branches below.
  const kitchenSection = completeKitchen
    ? <CompleteKitchenSection completeKitchen={completeKitchen} input={input} onSetClick={(set) => onKitchenSetClick?.(set)} />
    : null;

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
    // Sprint 10.170: a structured BLANK REPORT (not a friendly empty card) — it mirrors the filled report's
    // skeleton (metrics row → budget-split → category sections) so "empty" reads as a form awaiting the brief
    // and teaches the output shape. Recombines existing i18n keys only; invents no copy.
    return (
      <ResultShell>
        <div className="plans-column state-panel">
          {/* Sprint 10.171: a structured BLANK REPORT that mirrors the filled card grid (owner mockup) — card
              titles + hints stay readable; only the decorative "—" skeletons are aria-hidden. Existing keys only. */}
          <section className="report-panel empty-report" aria-label={t('results.emptyHeading')}>
            <div className="report-header">
              <div className="report-header-titles">
                <span className="report-kicker">{t('results.emptyBadge')}</span>
                <h3>{t('results.resultHeading')}</h3>
                <p>{t('results.overviewHint')}</p>
              </div>
            </div>
            <div className="report-grid is-empty">
              <section className="report-card report-card-budget">
                <div className="report-card-head"><CardIcon name="budget" /><h4>{t('results.budgetCardTitle')}</h4></div>
                <div className="report-budget-metrics" aria-hidden="true">
                  <div><span>{t('results.totalLabel')}</span><strong>—</strong></div>
                  <div><span>{t('results.remainingLabel')}</span><strong>—</strong></div>
                  <div><span>{t('results.storesLabel')}</span><strong>—</strong></div>
                </div>
                <div className="report-fill is-skeleton" aria-hidden="true"><span /></div>
              </section>
              <section className="report-card report-card-products">
                <div className="report-card-head"><CardIcon name="products" /><h4>{t('results.productsCardTitle')}</h4></div>
                <div className="report-empty-figure" aria-hidden="true"><span className="skeleton-thumb" /></div>
                <p className="report-card-hint">{t('results.productsEmptyHint')}</p>
              </section>
              <section className="report-card report-card-stores">
                <div className="report-card-head"><CardIcon name="stores" /><h4>{t('results.storesLabel')}</h4></div>
                <p className="report-card-hint">{t('results.storesEmptyHint')}</p>
                <ul className="report-store-list is-skeleton" aria-hidden="true">
                  <li><span className="skeleton-bar" style={{ width: '70%' }} /><strong>— €</strong></li>
                  <li><span className="skeleton-bar" style={{ width: '52%' }} /><strong>— €</strong></li>
                  <li><span className="skeleton-bar" style={{ width: '38%' }} /><strong>— €</strong></li>
                </ul>
              </section>
              <section className="report-card report-card-priorities">
                <div className="report-card-head"><CardIcon name="priorities" /><h4>{t('results.prioritiesTitle')}</h4></div>
                <p className="report-card-hint">{t('results.prioritiesEmptyHint')}</p>
                <ol className="report-priority-list is-skeleton" aria-hidden="true">
                  <li><span className="report-priority-rank">1</span><span className="skeleton-bar" style={{ width: '60%' }} /><strong>— €</strong></li>
                  <li><span className="report-priority-rank">2</span><span className="skeleton-bar" style={{ width: '48%' }} /><strong>— €</strong></li>
                  <li><span className="report-priority-rank">3</span><span className="skeleton-bar" style={{ width: '36%' }} /><strong>— €</strong></li>
                </ol>
              </section>
            </div>
            <div className="report-shopping-empty">
              <div className="report-card-head"><CardIcon name="list" /><h4>{t('results.shoppingListTitle')}</h4></div>
              <p className="report-card-hint">{t('results.shoppingListEmptyHint')}</p>
            </div>
          </section>
        </div>
      </ResultShell>
    );
  }

  // Sprint 10.168: the backend always returns 3 tiers, so plans.length is never 0 after a generation — an
  // empty result is 3 tiers each with 0 items. Show a clear "no products" state instead of a total=0 plan
  // that misleadingly reports "comfortably fits the budget".
  if (plans.every((plan) => plan.items.length === 0)) {
    return (
      <ResultShell>
        <div className="plans-column state-panel">
          {/* Sprint 10.175: a complete-kitchen prompt may leave the freestanding plan empty while still having real
              kitchen sets — show those (they ARE the result) and skip the misleading "no products" card. */}
          {kitchenSection}
          {!completeKitchen && (
            <section className="empty-report is-noresults" aria-label={t('results.noResultsHeading')}>
              <div className="empty-report-head">
                <span className="report-kicker">{t('results.noResultsBadge')}</span>
                <h3>{t('results.noResultsHeading')}</h3>
                <p>{t('planner.partialNone')}</p>
              </div>
            </section>
          )}
        </div>
      </ResultShell>
    );
  }

  const selectedPlan = plans.find((plan) => plan.id === selectedPlanId) ?? plans.find((plan) => plan.id === 'value') ?? plans[0];
  const overBudget = selectedPlan.total > input.budget;
  const breakdown = getRetailerBreakdown(selectedPlan);
  const budgetTight = selectedPlan.total >= input.budget * 0.92;
  const summaryBullets = selectedPlan.purchaseSummary ?? [];
  const repairTips = selectedPlan.budgetRepairSuggestions ?? [];
  const showBudgetBlock = repairTips.length > 0 || budgetTight;
  const missing = missingForRoom(selectedPlan, input);
  const steps = purchaseSteps(selectedPlan, input.roomType);
  // Sprint 10.171: the report grid summarises the plan. "Prioriteti" is the app's own assessment of what
  // matters first — items ranked by shopping priority (buy-first > add-comfort > later), then by price.
  const remaining = input.budget - selectedPlan.total;
  const priorityRank: Record<string, number> = { 'buy-first': 0, 'add-comfort': 1, later: 2 };
  const rankedItems = [...selectedPlan.items].sort((a, b) => {
    const d = (priorityRank[priorityForItem(a, input.roomType)] ?? 3) - (priorityRank[priorityForItem(b, input.roomType)] ?? 3);
    return d !== 0 ? d : b.product.price - a.product.price;
  });
  // Sprint 10.168: a clean shopping-list PDF (Print → "Save as PDF"). Build print sections from the same
  // purchase steps, and roll the items up by store for the "by store" summary.
  const printSections = steps.map((step) => ({
    title: t(step.titleKey),
    subtotal: step.subtotal,
    items: step.items.map((item) => {
      const qty = item.quantity && item.quantity > 1 ? item.quantity : 1;
      return {
        name: qty > 1 ? `${qty} × ${item.product.name}` : item.product.name,
        meta: `${item.product.retailer} · ${categoryLabels[item.product.category]}`,
        lineTotal: item.product.price * qty,
      };
    }),
  }));
  const printStores = Object.values(selectedPlan.items.reduce((acc, item) => {
    const qty = item.quantity && item.quantity > 1 ? item.quantity : 1;
    const retailer = item.product.retailer;
    acc[retailer] = acc[retailer] ?? { retailer, count: 0, total: 0 };
    acc[retailer].count += qty;
    acc[retailer].total += item.product.price * qty;
    return acc;
  }, {} as Record<string, { retailer: string; count: number; total: number }>)).sort((a, b) => b.total - a.total);
  const tier = TIER_LABEL_KEYS[selectedPlan.id]
    ? t(TIER_LABEL_KEYS[selectedPlan.id])
    : furnishingLevelLabels[input.furnishingLevel ?? 'comfort'];
  const selectedFeedback = feedbackByPlan[selectedPlan.id];
  const selectedFix = selectedFeedback ? FEEDBACK_ACTION[selectedFeedback] : undefined;

  return (
    <ResultShell>
      {/* GATING SEAM (Sprint 10.105 — one-time Design Session model). In the free beta everything below is shown
          for free (AuthContext premiumUnlocked === true). When one-time payments are wired, read `premiumUnlocked`
          from useAuth() and, when it's false, render a PREVIEW here (recommended style + budget-by-category +
          "N matching products found") instead of the full output, gating the exact products / store links /
          alternatives / downloadable list behind a purchased session. No premium feature is gated during beta. */}
      {kitchenSection}
      {partialNotice && (
        <div className="partial-plan-note" role="status">
          <strong>{t('results.partialPlan')}</strong>
          <span>{partialNotice}</span>
        </div>
      )}
      {input.roomType === 'kitchen' && !completeKitchen && (
        isFittedKitchenIntent(submittedPrompt || input.prompt) ? (
          <div className="kitchen-scope-note is-fitted" role="note">
            <strong>{t('results.kitchenFittedNote')}</strong>
            <a
              className="kitchen-scope-link"
              href={ikeaMarketUrl(input.market)}
              target="_blank"
              rel="noopener noreferrer"
            >
              {t('results.kitchenFittedLink')} ↗
            </a>
          </div>
        ) : (
          <p className="kitchen-scope-note" role="note">{t('results.kitchenNote')}</p>
        )
      )}
      <div className="plans-column decision-results-column">
        <article className="plan-card focused-plan-card decision-plan-card" key={selectedPlan.id}>
          <div className="decision-card">
            <div className="decision-result-header">
              <div className="decision-result-title">
                <span className="step-kicker">{t('results.resultKicker')}</span>
                <h3>{t('results.resultHeading')}</h3>
                <small className="result-price-caveat" role="note">{t('results.priceEstimateShort')}</small>
              </div>
              <span className="result-info">
                <button
                  type="button"
                  className="result-info-btn"
                  aria-label={t('results.priceEstimateNote')}
                  aria-expanded={showPriceInfo}
                  onClick={() => setShowPriceInfo((value) => !value)}
                >
                  ?
                </button>
                {showPriceInfo && (
                  <span className="result-info-tip" role="tooltip">{t('results.priceEstimateNote')}</span>
                )}
              </span>
            </div>
            {!localize && summaryBullets.length > 0 ? (
              <ul className="purchase-summary">
                {summaryBullets.map((line) => (
                  <li key={line}>{line}</li>
                ))}
              </ul>
            ) : (
              <p>{localize ? defaultSummary(t, selectedPlan, input) : (selectedPlan.advisorNote || defaultSummary(t, selectedPlan, input))}</p>
            )}

            {/* Sprint 10.171: the plan REPORT as a card grid — Budžet / Proizvodi / Trgovine / Prioriteti (owner
                mockup). Read-only summaries from the existing plan data; the actionable "Popis za kupnju" and all
                detail panels follow below, unchanged. */}
            <div className="report-grid" aria-label={t('results.planKeyInfo')}>
              <section className="report-card report-card-budget">
                <div className="report-card-head"><CardIcon name="budget" /><h4>{t('results.budgetCardTitle')}</h4></div>
                <div className="report-budget-metrics">
                  <div><span>{t('results.totalLabel')}</span><strong>{formatCurrency(selectedPlan.total)}</strong></div>
                  <div><span>{overBudget ? t('results.needToReduce') : t('results.remainingLabel')}</span><strong className={remaining < 0 ? 'over-text' : ''}>{formatCurrency(Math.abs(remaining))}</strong></div>
                  <div><span>{t('results.storesLabel')}</span><strong>{selectedPlan.retailersUsed.length || 0}</strong></div>
                </div>
                <div className="report-fill" aria-hidden="true"><span className={remaining < 0 ? 'over' : ''} style={{ width: `${Math.min(100, Math.round((selectedPlan.total / Math.max(1, input.budget)) * 100))}%` }} /></div>
              </section>

              <section className="report-card report-card-products">
                <div className="report-card-head"><CardIcon name="products" /><h4>{t('results.productsCardTitle')}</h4><span className="report-card-count">{t('results.productsCount', { count: selectedPlan.items.length })}</span></div>
                <div className="report-thumbs">
                  {selectedPlan.items.slice(0, 6).map((item) => (
                    <span className="report-thumb" key={item.product.id}>
                      <img src={productImage(item.product)} alt="" loading="lazy" onError={(event) => handleProductImageError(event, item.product.category)} />
                    </span>
                  ))}
                  {selectedPlan.items.length > 6 && <span className="report-thumb report-thumb-more">+{selectedPlan.items.length - 6}</span>}
                </div>
              </section>

              <section className="report-card report-card-stores">
                <div className="report-card-head"><CardIcon name="stores" /><h4>{t('results.storesLabel')}</h4></div>
                {/* Sprint 10.171: use the qty-aware printStores (price × quantity), NOT getRetailerBreakdown,
                    so a multi-qty item (e.g. 6 chairs) counts fully and the per-store totals sum to the Budžet total. */}
                <ul className="report-store-list">
                  {printStores.map((entry) => (
                    <li key={entry.retailer}>
                      <span className="report-store-name">{entry.retailer}</span>
                      <span className="report-store-bar" aria-hidden="true"><span style={{ width: `${Math.round((entry.total / Math.max(1, selectedPlan.total)) * 100)}%` }} /></span>
                      <strong>{formatCurrency(entry.total)}</strong>
                    </li>
                  ))}
                </ul>
                <div className="report-store-total"><span>{t('results.storesTotalLabel')}</span><strong>{selectedPlan.retailersUsed.length || 0}</strong></div>
              </section>

              <section className="report-card report-card-priorities">
                <div className="report-card-head"><CardIcon name="priorities" /><h4>{t('results.prioritiesTitle')}</h4></div>
                <ol className="report-priority-list">
                  {rankedItems.slice(0, 3).map((item, i) => (
                    <li key={item.product.id}>
                      <span className="report-priority-rank">{i + 1}</span>
                      <span className="report-priority-name">{categoryLabels[item.product.category]}</span>
                      <strong>{formatCurrency(item.product.price * (item.quantity && item.quantity > 1 ? item.quantity : 1))}</strong>
                    </li>
                  ))}
                </ol>
              </section>
            </div>

            {showBudgetBlock && (
              <div className="budget-pressure-strip">
                <strong>{t('results.budgetTight')}</strong>
                {!localize && repairTips.length > 0 ? (
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

            {/* Sprint 10.172: removed the green store-advice box — the "Trgovine" report card already lists the
                stores and amounts, so this sentence was redundant. */}
            {!localize && selectedPlan.storeLimitNote && (
              <div className="store-limit-note">{selectedPlan.storeLimitNote}</div>
            )}

            <div className="decision-actions">
              <button className="plan-button primary-copy-button" type="button" onClick={() => copyPlan(selectedPlan)}>
                {copiedPlanId === selectedPlan.id ? t('results.listCopied') : t('results.copyShoppingList')}
              </button>
              {/* Sprint 10.170: the three quiet actions group into a right-aligned toolbar (not four stacked
                  full-width slabs) — primary Copy on the left, Save · Link · PDF as a secondary cluster. */}
              <div className="toolbar-secondary">
              <button className="share-button soft" type="button" onClick={() => saveCurrentPlan(selectedPlan, false)} disabled={savingPlanId === selectedPlan.id}>
                {savingPlanId === selectedPlan.id ? t('results.saving') : t('results.saveToMyPlans')}
              </button>
              <button className="share-button soft" type="button" onClick={() => saveCurrentPlan(selectedPlan, true)} disabled={savingPlanId === selectedPlan.id}>
                {t('results.copyLink')}
              </button>
              <button className="share-button soft" type="button" onClick={() => void openPlanPdf({
                title: roomLabels[input.roomType],
                subtitle: styleLabels[input.style],
                budget: input.budget,
                total: selectedPlan.total,
                sections: printSections,
                stores: printStores,
                money: (value) => formatCurrency(value, input.market),
                labels: {
                  shoppingList: t('print.shoppingList'), budget: t('print.budget'), total: t('print.total'),
                  remaining: t('print.remaining'), over: t('print.over'), byStore: t('print.byStore'),
                  disclaimer: t('print.disclaimer'), madeWith: t('print.madeWith'),
                  itemsCount: (count) => t('moveIn.itemsCount', { count }),
                },
              })}>
                {t('print.downloadPdf')}
              </button>
              </div>
            </div>
          </div>

          <div className="items-list step-items-list">
            {/* Sprint 10.171: "Popis za kupnju" — one flat product list. The buy-first / add-comfort / later
                DIVISION is gone (the Prioriteti card ranks importance now); this lists every product with its
                actions, keeping open-in-store / replace / keep / watch exactly as before. */}
            <div className="result-section-heading">
              <span>{t('results.shoppingListTitle')}</span>
              <p>{t('results.productsInPlanHint')}</p>
            </div>
            <section className="step-product-section flat-product-list">
                {selectedPlan.items.map((item) => {
                  const { product } = item;
                  const locked = lockedProductIds.includes(product.id);
                  const priority = priorityForItem(item, input.roomType);
                  const expanded = expandedProductId === product.id;
                  const actionsOpen = actionsProductId === product.id;
                  const similarOpen = similarProductId === product.id;
                  const openUrl = productUrl(product);
                  const market = marketBadge(product);
                  const illustration = usesFallbackImage(product);
                  const sale = saleInfo(product);
                  // Sprint 10.120: show the requested count ("6 ×") and the line total (price × count).
                  const qty = item.quantity && item.quantity > 1 ? item.quantity : 1;
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
                          <strong>{qty > 1 ? `${qty} × ${product.name}` : product.name}</strong>
                          <span>
                            {formatCurrency(product.price * qty)}
                            {qty > 1 && (
                              <small className="qty-unit-note">{qty} × {formatCurrency(product.price)}</small>
                            )}
                            {sale && (
                              <s className="original-price" title={t('results.regularPrice', { price: formatCurrency(sale.original) })}>
                                {formatCurrency(sale.original * qty)}
                              </s>
                            )}
                          </span>
                        </div>
                        <div className="meta-line">
                          <span>{product.retailer}</span>
                          {market && <span title={t('results.marketCatalogTitle', { market })}>{t('results.marketLabel', { market })}</span>}
                          <span>{categoryLabels[product.category]}</span>
                          <span className={`priority-chip ${priority}`}>{shoppingPriorityLabels[priority]}</span>
                          <span>{availabilityLabel(t, product)}</span>
                          {hasReviews(t, product) && (
                            // Sprint 10.163 (Omnibus review rules): the softened default tooltip no longer claims
                            // "(verified)"; when the product is stale we can't re-verify the rating, so the tooltip
                            // becomes explicitly time-qualified. The click-through to the store's reviews is kept below.
                            <span className="review-chip" title={isStaleProduct(product) ? t('results.reviewChipTitleStale') : t('results.reviewChipTitle')}>
                              {reviewSummary(t, product)}
                            </span>
                          )}
                          {illustration && (
                            <span title={t('results.illustrationTitle')}>
                              {t('results.illustrationChip')}
                            </span>
                          )}
                          {sale && (
                            <span className="sale-chip" title={t('results.regularPrice', { price: formatCurrency(sale.original) })}>
                              {t('results.onSale')}
                            </span>
                          )}
                          {sale && (
                            <span className="sale-saving">
                              {t('results.saleSaving', { percent: sale.percent, amount: formatCurrency(sale.amount) })}
                            </span>
                          )}
                          {sale?.endsAt && (
                            <span className="sale-ends" title={t('results.checkPriceInStore')}>
                              {t('results.saleEnds', { date: formatSaleEndDate(sale.endsAt) })}
                            </span>
                          )}
                          {locked && <span>{t('results.kept')}</span>}
                        </div>
                        <small className="product-shop-note">{priceTierLabel(t, product)} · {productCheckLabel(t, product)}</small>
                        {/* The delivery note is a generic "check delivery/pickup in store" caveat, so always show the
                            localized UI string — never the per-product deliveryNote, which is stored in English for many
                            catalog rows (IKEA cross-market port, AT/DE/SI) and would leak English into a non-EN UI. */}
                        {product.deliveryNote && <small className="product-delivery-note">{t('results.deliveryNoteGeneric')}</small>}
                        <div className="product-actions decision-product-actions">
                          {openUrl ? (
                            <a className="open-store-link" href={openUrl} target="_blank" rel="noopener noreferrer" onClick={() => onProductClick(selectedPlan.id, product)}>
                              {t('results.openInStore')}
                            </a>
                          ) : (
                            <button type="button" disabled title={t('results.productLinkUnavailableTitle')}>
                              {t('results.productLinkUnavailable')}
                            </button>
                          )}
                          <button type="button" className="more-options-toggle" aria-expanded={actionsOpen} onClick={() => toggleActions(product.id)}>
                            {actionsOpen ? t('results.hideOptions') : t('results.moreOptions')}
                          </button>
                          {/* Sprint 10.173 (P0): open the "similar under budget" discovery panel for this row. */}
                          <button type="button" className="similar-toggle" aria-expanded={similarOpen} onClick={() => setSimilarProductId(similarOpen ? null : product.id)}>
                            {t('similar.action')}
                          </button>
                        </div>
                        {actionsOpen && (
                          <div className="product-actions product-secondary-actions">
                            <button type="button" aria-expanded={expanded} onClick={() => setExpandedProductId(expanded ? null : product.id)} disabled={locked}>
                              {expanded ? t('results.hideReplacements') : t('results.change')}
                            </button>
                            <button type="button" onClick={() => onToggleLock(product.id)}>
                              {locked ? t('results.release') : t('results.keep')}
                            </button>
                          </div>
                        )}
                        {actionsOpen && expanded && !locked && (
                          <div className="replacement-menu" aria-label={t('results.replacementMenuLabel')}>
                            <button type="button" onClick={() => void requestReplace(selectedPlan.id, product.id, 'cheaper')}>{t('results.findCheaper')}</button>
                            <button type="button" onClick={() => void requestReplace(selectedPlan.id, product.id, 'nicer')}>{t('results.findNicer')}</button>
                            <button type="button" aria-expanded={dislikeProductId === product.id} onClick={() => setDislikeProductId(dislikeProductId === product.id ? null : product.id)}>{t('results.dontLikeIt')}</button>
                            <button type="button" onClick={() => void onReplace(selectedPlan.id, product.id, 'remove')}>{t('results.dontNeedThis')}</button>
                            {dislikeProductId === product.id && (
                              <div className="dislike-reasons">
                                <span>{t('results.whatsWrong')}</span>
                                <button type="button" onClick={() => void requestReplace(selectedPlan.id, product.id, 'cheaper')}>{t('results.tooExpensive')}</button>
                                <button type="button" onClick={() => void requestReplace(selectedPlan.id, product.id, 'nicer')}>{t('results.wantNicerStyle')}</button>
                                <button type="button" onClick={() => void requestReplace(selectedPlan.id, product.id, 'different')}>{t('results.showAnother')}</button>
                                <button type="button" onClick={() => void onReplace(selectedPlan.id, product.id, 'remove')}>{t('results.dontNeedThatItem')}</button>
                              </div>
                            )}
                            {noSwapProductId === product.id && (
                              <p className="replacement-empty" role="status">{t(noSwapMessageKey)}</p>
                            )}
                          </div>
                        )}
                        {similarOpen && (
                          <SimilarItemsPanel
                            key={product.id}
                            anchor={product}
                            input={input}
                            remainingBudget={input.budget - selectedPlan.total}
                            onOpenProduct={(picked, bucket, cap) => onSimilarProductOpen(selectedPlan.id, picked, bucket, cap)}
                          />
                        )}
                      </div>
                    </div>
                  );
                })}
            </section>
          </div>

          {/* Sprint 10.62: one calm "more" panel collapses the supporting detail (breakdown, shopping-by-store,
              version comparison, full plan details, what-we-understood) so the default view stays the verdict +
              products. Closed by default — opened when a user wants to dig in. Nothing was removed. */}
          <details className="more-about-plan">
            <summary>
              <span>{t('results.moreAboutPlan')}</span>
              <small>{t('results.moreAboutPlanHint')}</small>
            </summary>

            <BudgetBreakdown plan={selectedPlan} input={input} />

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
                // Sprint 10.153: localize the tier name here too (was the one ungated site → Croatian "Najbolji
                // izbor" etc. leaked into the compare-versions grid for every non-HR / read-in-English user).
                const planTier = TIER_LABEL_KEYS[plan.id] ? t(TIER_LABEL_KEYS[plan.id]) : plan.name;
                return (
                  <button type="button" key={plan.id} className={active ? 'plan-choice-card active' : 'plan-choice-card'} onClick={() => setSelectedPlanId(plan.id)}>
                    <span>{localize ? planTier : plan.name}</span>
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
              <p>{localize ? defaultSummary(t, selectedPlan, input) : (selectedPlan.summary || defaultSummary(t, selectedPlan, input))}</p>
              <small>{localize ? localBudgetStatus(t, selectedPlan, input) : selectedPlan.budgetStatus}</small>
            </div>

            <div className="plan-card-header compact-plan-header">
              <div>
                {!localize && <span className="plan-label">{selectedPlan.label}</span>}
                <h3>{localize ? tier : selectedPlan.name}</h3>
                {tier && <small className="plan-tier">{t('results.tierEquipment', { tier })}</small>}
              </div>
              <div className={overBudget ? 'total over' : 'total'}>{formatCurrency(selectedPlan.total)}</div>
            </div>

            <div className="plan-explainer-grid">
              <div>
                <span>{t('results.whoIsThisFor')}</span>
                <p>{localize ? t('results.whoIsThisForDefault') : (selectedPlan.goodFor || t('results.whoIsThisForDefault'))}</p>
              </div>
              <div>
                <span>{t('results.whatToWatch')}</span>
                <p>{localize ? t('results.tradeoffDefault') : (selectedPlan.tradeoff || selectedPlan.description || t('results.tradeoffDefault'))}</p>
              </div>
            </div>

            <div className="tradeoff-panel" aria-label={t('results.budgetTips')}>
              <div className="tradeoff-card save">
                <span>{t('results.howToLowerPrice')}</span>
                <ul>
                  {(!localize && selectedPlan.savingTips?.length ? selectedPlan.savingTips : [t('results.savingTipDefault')]).map((tip) => (
                    <li key={tip}>{tip}</li>
                  ))}
                </ul>
              </div>
              <div className="tradeoff-card upgrade">
                <span>{t('results.ifYouCanAddMore')}</span>
                <ul>
                  {(!localize && selectedPlan.upgradeTips?.length ? selectedPlan.upgradeTips : [t('results.upgradeTipDefault')]).map((tip) => (
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
          </details>

          {SHOW_SECOND_HAND && <SecondHandSection products={secondHandSuggestions} planId={selectedPlan.id} onProductClick={onProductClick} />}

          <SharePanel plan={selectedPlan} input={input} onSavePlan={onSavePlan} />

          <div className="feedback-card compact-feedback-card">
            <span>{t('results.isPlanGood')}</span>
            <div className="feedback-buttons">
              {feedbackOptions.map((option) => (
                <button type="button" key={option.value} className={selectedFeedback === option.value ? 'active' : ''} onClick={() => sendFeedback(selectedPlan.id, option.value)}>
                  {t(option.labelKey)}
                </button>
              ))}
            </div>
            {selectedFeedback && (
              <div className="feedback-followup">
                {selectedFix ? (
                  <>
                    <span>{t('results.feedbackThanksActable')}</span>
                    <button type="button" className="feedback-action-button" onClick={() => onQuickAction(selectedFix.action, selectedPlan)}>
                      {t(selectedFix.labelKey)}
                    </button>
                  </>
                ) : (
                  <span className="feedback-thanks">{t('results.feedbackThanks')}</span>
                )}
              </div>
            )}
          </div>
        </article>
      </div>
    </ResultShell>
  );
}
