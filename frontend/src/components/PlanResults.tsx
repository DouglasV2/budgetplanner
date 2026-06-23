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
import { useAuth } from '../AuthContext';
import { watchProduct } from '../api/client';

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
  // Sprint 10.51: matched second-hand listings, shown in a separate "Rabljeno" block (never in any total).
  secondHandSuggestions?: Product[];
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
  nightstand: 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=240&q=70',
  wardrobe: 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=240&q=70',
  dresser: 'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=240&q=70'
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

function decisionLabel(t: Translate, plan: FurnishingPlan, input: PlannerInput) {
  const difference = input.budget - plan.total;
  if (difference < 0) return t('results.decisionNotIdeal');
  if (difference >= input.budget * 0.12) return t('results.decisionWorthSafe');
  return t('results.decisionWorthClose');
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
const BUDGET_COLORS = ['#CF5F2A', '#66785F', '#B0894E', '#7C786F', '#9C6B4A', '#5E7488', '#A88A5C'];

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
  partialNotice = null,
  secondHandSuggestions = []
}: PlanResultsProps) {
  const { t } = useLocale();
  const [copiedPlanId, setCopiedPlanId] = useState<string | null>(null);
  const [savingPlanId, setSavingPlanId] = useState<string | null>(null);
  const [feedbackByPlan, setFeedbackByPlan] = useState<Record<string, PlanFeedback>>({});
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);
  const [expandedProductId, setExpandedProductId] = useState<string | null>(null);
  const [dislikeProductId, setDislikeProductId] = useState<string | null>(null);
  // Sprint 10.62: per-product secondary actions (change / keep / watch / reviews) hide behind one toggle so a
  // row shows just the price, the reason and "open in store" by default. Only one row's actions open at a time.
  const [actionsProductId, setActionsProductId] = useState<string | null>(null);
  // Sprint 10.34: opt-in price-drop watch (one inline form open at a time).
  const [watchProductId, setWatchProductId] = useState<string | null>(null);
  const [watchEmail, setWatchEmail] = useState('');
  const [watchConsent, setWatchConsent] = useState(false);
  const [watchSubmitting, setWatchSubmitting] = useState(false);
  const [watchStatus, setWatchStatus] = useState<{ id: string; type: 'ok' | 'error'; message: string } | null>(null);

  // Sprint 10.62: a genuinely new plan SET (fresh generation / opened plan) is identified by its plan ids; an
  // in-place product replace keeps the same ids. We key the per-row disclosure reset on the ids so the
  // "More options" row a user is editing survives an iterative replace instead of snapping shut under them.
  const planIdsKey = plans.map((plan) => plan.id).join('|');

  // Runs on every plans change: keep the selected version in sync, and clear stale feedback (Sprint 10.54: a
  // fresh plan set means old feedback no longer applies, so a stale "make it cheaper?" CTA never lingers).
  useEffect(() => {
    setSelectedPlanId(preferredPlanId(plans, input));
    setFeedbackByPlan({});
  }, [plans, input.optimizationGoal, input.furnishingLevel]);

  // Runs only on a genuinely new plan set (new ids) or a goal/level switch — NOT on an in-place replace — so a
  // row the user has open for editing stays open while they try cheaper/nicer swaps on the same item.
  useEffect(() => {
    setExpandedProductId(null);
    setDislikeProductId(null);
    setActionsProductId(null);
    setWatchProductId(null);
    setWatchStatus(null);
  }, [planIdsKey, input.optimizationGoal, input.furnishingLevel]);

  function openWatchForm(productId: string) {
    setWatchProductId(watchProductId === productId ? null : productId);
    setWatchStatus(null);
    setWatchEmail('');
    setWatchConsent(false);
  }

  // Sprint 10.62: open/close a row's secondary actions. Toggling always collapses that row's nested menus so a
  // fresh open starts clean and a closed row never leaves an orphaned replacement/watch panel showing.
  function toggleActions(productId: string) {
    setActionsProductId((current) => (current === productId ? null : productId));
    setExpandedProductId(null);
    setDislikeProductId(null);
    setWatchProductId(null);
    setWatchStatus(null);
  }

  async function submitWatch(product: Product) {
    if (!watchConsent) {
      setWatchStatus({ id: product.id, type: 'error', message: t('results.watchConsentRequired') });
      return;
    }
    setWatchSubmitting(true);
    try {
      const res = await watchProduct({
        email: watchEmail.trim(),
        externalId: product.externalId || product.id,
        market: product.market,
        consent: true
      });
      setWatchStatus({
        id: product.id,
        type: 'ok',
        message: res.alreadyWatching ? t('results.watchAlready') : t('results.watchSuccess', { email: res.email })
      });
      setWatchProductId(null);
      setWatchEmail('');
      setWatchConsent(false);
    } catch (error) {
      setWatchStatus({
        id: product.id,
        type: 'error',
        message: error instanceof Error ? error.message : t('results.watchError')
      });
    } finally {
      setWatchSubmitting(false);
    }
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
  const selectedFix = selectedFeedback ? FEEDBACK_ACTION[selectedFeedback] : undefined;

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
                  const actionsOpen = actionsProductId === product.id;
                  const openUrl = productUrl(product);
                  const reviewsHref = reviewsUrl(product);
                  const market = marketBadge(product);
                  const illustration = usesFallbackImage(product);
                  const sale = saleInfo(product);
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
                          <span>
                            {formatCurrency(product.price)}
                            {sale && (
                              <s className="original-price" title={t('results.regularPrice', { price: formatCurrency(sale.original) })}>
                                {formatCurrency(sale.original)}
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
                            <span className="review-chip" title={t('results.reviewChipTitle')}>
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
                        {product.deliveryNote && <small className="product-delivery-note">{product.deliveryNote}</small>}
                        <div className="product-reason-box compact-reason-box">
                          <span>{item.shoppingRole || t('results.whyThis')}</span>
                          <p>{reason}</p>
                        </div>
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
                        </div>
                        {actionsOpen && (
                          <div className="product-actions product-secondary-actions">
                            {hasReviews(t, product) && reviewsHref && (
                              <a className="reviews-link" href={reviewsHref} target="_blank" rel="noopener noreferrer" onClick={() => onProductClick(selectedPlan.id, product)}>
                                {t('product.reviews')} ↗
                              </a>
                            )}
                            <button type="button" aria-expanded={expanded} onClick={() => setExpandedProductId(expanded ? null : product.id)} disabled={locked}>
                              {expanded ? t('results.hideReplacements') : t('results.change')}
                            </button>
                            <button type="button" onClick={() => onToggleLock(product.id)}>
                              {locked ? t('results.release') : t('results.keep')}
                            </button>
                            <button type="button" className="watch-price-button" aria-expanded={watchProductId === product.id} onClick={() => openWatchForm(product.id)}>
                              {t('results.watchPrice')}
                            </button>
                          </div>
                        )}
                        {actionsOpen && expanded && !locked && (
                          <div className="replacement-menu" aria-label={t('results.replacementMenuLabel')}>
                            <button type="button" onClick={() => onReplace(selectedPlan.id, product.id, 'cheaper')}>{t('results.findCheaper')}</button>
                            <button type="button" onClick={() => onReplace(selectedPlan.id, product.id, 'nicer')}>{t('results.findNicer')}</button>
                            <button type="button" aria-expanded={dislikeProductId === product.id} onClick={() => setDislikeProductId(dislikeProductId === product.id ? null : product.id)}>{t('results.dontLikeIt')}</button>
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
                        {actionsOpen && watchProductId === product.id && (
                          <div className="price-watch-form" aria-label={t('results.watchTitle')}>
                            <span className="price-watch-title">{t('results.watchTitle')}</span>
                            <input
                              type="email"
                              className="price-watch-email"
                              value={watchEmail}
                              onChange={(event) => setWatchEmail(event.target.value)}
                              placeholder={t('results.watchEmailPlaceholder')}
                              aria-label={t('results.watchEmailPlaceholder')}
                            />
                            <label className="price-watch-consent">
                              <input type="checkbox" checked={watchConsent} onChange={(event) => setWatchConsent(event.target.checked)} />
                              <span>{t('results.watchConsent')}</span>
                            </label>
                            <div className="price-watch-actions">
                              <button type="button" className="price-watch-submit" onClick={() => submitWatch(product)} disabled={watchSubmitting}>
                                {watchSubmitting ? t('results.watchSubmitting') : t('results.watchSubmit')}
                              </button>
                              <button type="button" onClick={() => setWatchProductId(null)}>{t('results.watchCancel')}</button>
                            </div>
                          </div>
                        )}
                        {watchStatus?.id === product.id && (
                          <small className={watchStatus.type === 'ok' ? 'price-watch-status ok' : 'price-watch-status error'}>
                            {watchStatus.message}
                          </small>
                        )}
                      </div>
                    </div>
                  );
                })}
              </section>
            ))}
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
          </details>

          <SecondHandSection products={secondHandSuggestions} planId={selectedPlan.id} onProductClick={onProductClick} />

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
