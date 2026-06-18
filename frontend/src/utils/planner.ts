import type { FurnishingLevel, FurnishingPlan, PlanItem, PlannerInput, Product, ProductCategory, Retailer, RoomType, ShoppingPriority, StoreTotal, StoreTrip, StyleType } from '../types';
import { marketConfig } from '../markets';
import { translate } from '../i18n';

// Sprint 10.13/10.47: the active market drives BOTH currency formatting and domain-label translation
// app-wide (LocaleProvider sets it on country switch). Domain labels read the market's language so
// category/room/style/level/priority names are localised instead of hardcoded Croatian.
let activeMarket = 'HR';
export function setFormattingMarket(market: string | undefined) {
  activeMarket = market ?? 'HR';
}
function labelLang() {
  return marketConfig(activeMarket).lang;
}
function tLabel(key: string, params?: Record<string, string | number>) {
  return translate(key, labelLang(), params);
}
// Turn an enum→i18n-key map into a string map that translates on access, so every existing
// `categoryLabels[x]` call site stays unchanged but now returns the active language.
function localisedLabels<K extends string>(keys: Record<K, string>): Record<K, string> {
  return new Proxy({} as Record<K, string>, {
    get: (_target, prop) => (typeof prop === 'string' ? tLabel(keys[prop as K] ?? prop) : undefined)
  });
}

export const retailers: Retailer[] = ['IKEA', 'JYSK', 'Pevex', 'Emmezeta', 'Decathlon', 'Lesnina'];

// Sprint 10.30: which retailers actually have verified products per market (mirrors the catalog + the
// backend PlannerService.RETAILERS list). The store picker offers these per selected country, and the
// "best combo" mode selects exactly them — so a plan never silently includes a store with no local
// products, and the previously-unreachable Harvey Norman / Namjestaj.hr / Otto / Segmüller / Poco
// products now become selectable in their markets.
export const retailersByMarket: Record<string, Retailer[]> = {
  // Sprint 10.48: retail re-sweep added one or more verified retailers to most markets.
  HR: ['IKEA', 'JYSK', 'Emmezeta', 'Harvey Norman', 'Namjestaj.hr', 'Svijetnamještaja'],
  SI: ['IKEA', 'JYSK', 'Harvey Norman', 'Svetpohištva'],
  AT: ['IKEA', 'JYSK', 'Interio'],
  DE: ['IKEA', 'JYSK', 'Otto', 'Segmüller', 'Poco'],
  IT: ['IKEA', 'Conforama'],
  FI: ['IKEA', 'JYSK', 'Masku'],
  FR: ['IKEA', 'Camif', 'Lovely Meubles'],
  NL: ['IKEA', 'JYSK', 'Leen Bakker', 'Kwantum', 'Pronto Wonen'],
  SK: ['IKEA', 'JYSK', 'Nábytok', 'Drevona', 'ASKO Nábytok'],
  ES: ['IKEA', 'Kenay Home', 'Banak Importa', 'Miroytengo', 'Merkamueble', 'Muebles BOOM'],
  PT: ['IKEA', 'Moviflor', 'JOM', 'Sítio do Móvel'],
  // Sprint 10.46: Scandinavia — IKEA + JYSK, prices in the national currency.
  NO: ['IKEA', 'JYSK'],
  SE: ['IKEA', 'JYSK'],
  DK: ['IKEA', 'JYSK'],
};

export function retailersForMarket(code?: string): Retailer[] {
  return retailersByMarket[(code ?? 'HR').toUpperCase()] ?? ['IKEA', 'JYSK'];
}

// Category + priority names are new i18n keys (cat.* / priority.*); room/style/level reuse the form.*
// option keys that are already translated in every language. All resolve to the active market language.
export const categoryLabels: Record<ProductCategory, string> = localisedLabels<ProductCategory>({
  sofa: 'cat.sofa',
  chair: 'cat.chair',
  table: 'cat.table',
  'tv-unit': 'cat.tv-unit',
  storage: 'cat.storage',
  rug: 'cat.rug',
  lighting: 'cat.lighting',
  decor: 'cat.decor',
  desk: 'cat.desk',
  bed: 'cat.bed',
  mattress: 'cat.mattress',
  'gym-equipment': 'cat.gym-equipment',
  'dining-table': 'cat.dining-table',
  'dining-chair': 'cat.dining-chair',
  'kitchen-storage': 'cat.kitchen-storage',
  'kitchen-cart': 'cat.kitchen-cart',
  nightstand: 'cat.nightstand',
  wardrobe: 'cat.wardrobe',
  dresser: 'cat.dresser'
});

export const roomLabels: Record<RoomType, string> = localisedLabels<RoomType>({
  'living-room': 'form.roomLivingRoomLabel',
  'home-office': 'form.roomHomeOfficeLabel',
  bedroom: 'form.roomBedroomLabel',
  'home-gym': 'form.roomHomeGymLabel',
  kitchen: 'form.roomKitchenLabel',
  'dining-room': 'form.roomDiningRoomLabel',
  hallway: 'form.roomHallwayLabel',
  bathroom: 'form.roomBathroomLabel'
});

export const furnishingLevelLabels: Record<FurnishingLevel, string> = localisedLabels<FurnishingLevel>({
  basic: 'form.furnishingBasicLabel',
  comfort: 'form.furnishingComfortLabel',
  complete: 'form.furnishingCompleteLabel'
});

export const shoppingPriorityLabels: Record<ShoppingPriority, string> = localisedLabels<ShoppingPriority>({
  'buy-first': 'priority.buy-first',
  'add-comfort': 'priority.add-comfort',
  later: 'priority.later'
});

export const styleLabels: Record<StyleType, string> = localisedLabels<StyleType>({
  bright: 'form.styleBrightLabel',
  warm: 'form.styleWarmLabel',
  modern: 'form.styleModernLabel',
  minimal: 'form.styleMinimalLabel',
  classic: 'form.styleClassicLabel',
  industrial: 'form.styleIndustrialLabel',
  boho: 'form.styleBohoLabel',
  surprise: 'form.styleSurpriseLabel',
  scandinavian: 'form.styleBrightLabel',
  cozy: 'form.styleWarmLabel'
});

export function getRetailerBreakdown(plan: FurnishingPlan) {
  const breakdown = plan.items.reduce<Record<string, { total: number; count: number; items: PlanItem[] }>>((acc, item) => {
    const retailer = item.product.retailer;
    acc[retailer] ??= { total: 0, count: 0, items: [] };
    acc[retailer].total += item.product.price;
    acc[retailer].count += 1;
    acc[retailer].items.push(item);
    return acc;
  }, {});

  return Object.entries(breakdown)
    .map(([retailer, value]) => ({ retailer: retailer as Retailer, ...value }))
    .sort((a, b) => b.total - a.total);
}

export function isCheckInStore(product: Product) {
  const status = product.availabilityStatus || (product.inStock === false ? 'unavailable' : 'in-stock');
  return status === 'limited' || status === 'check-store';
}

function storesWord(count: number) {
  const plural = new Intl.PluralRules(marketConfig(activeMarket).locale).select(count);
  return tLabel(plural === 'one' ? 'unit.storesOne' : 'unit.storesOther');
}

export function storeCountText(count: number) {
  return `${count} ${storesWord(count)}`;
}

function buildStoreTripRecommendation(storeCount: number, mainRetailer: Retailer | null, checkInStoreCount: number) {
  let base: string;
  if (storeCount <= 0 || !mainRetailer) {
    base = tLabel('storeTrip.empty');
  } else if (storeCount === 1) {
    base = tLabel('storeTrip.single', { store: mainRetailer });
  } else {
    base = tLabel('storeTrip.multi', { store: mainRetailer, count: storeCount, stores: storesWord(storeCount) });
  }
  if (checkInStoreCount > 0) {
    base += ' ' + tLabel(checkInStoreCount === 1 ? 'storeTrip.checkOne' : 'storeTrip.checkMany');
  }
  return base;
}

// Prefer the store trip the backend already computed. Older saved plans were
// created before Sprint 8.4, so fall back to rebuilding it from the items.
export function resolveStoreTrip(plan: FurnishingPlan): StoreTrip {
  if (plan.storeTrip && plan.storeTrip.stores?.length) {
    return plan.storeTrip;
  }
  const stores: StoreTotal[] = getRetailerBreakdown(plan).map((entry) => ({
    retailer: entry.retailer,
    total: entry.total,
    itemCount: entry.count
  }));
  const storeCount = stores.length;
  const mainRetailer = stores[0]?.retailer ?? null;
  const checkInStoreCount = plan.items.filter((item) => isCheckInStore(item.product)).length;
  return {
    storeCount,
    mainRetailer,
    mainRetailerTotal: stores[0]?.total ?? 0,
    checkInStoreCount,
    recommendation: buildStoreTripRecommendation(storeCount, mainRetailer, checkInStoreCount),
    stores
  };
}

export function formatPlanForSharing(plan: FurnishingPlan, input: PlannerInput) {
  const storeSections = getRetailerBreakdown(plan).flatMap((entry) => [
    `${entry.retailer} — ${tLabel('share.items', { count: entry.count })} — ${formatCurrency(entry.total)}`,
    ...entry.items.map((item) => `- ${item.product.name} — ${formatCurrency(item.product.price)} (${shoppingPriorityLabels[item.shoppingPriority ?? 'later']})`),
    ''
  ]);

  return [
    tLabel('share.title', { room: roomLabels[input.roomType] }),
    `${tLabel('share.budget')}: ${formatCurrency(input.budget)}`,
    `${tLabel('share.total')}: ${formatCurrency(plan.total)}${plan.total <= input.budget ? ` · ${tLabel('share.remains', { amount: formatCurrency(input.budget - plan.total) })}` : ` · ${tLabel('share.over', { amount: formatCurrency(plan.total - input.budget) })}`}`,
    `${tLabel('share.stores')}: ${plan.retailersUsed.join(', ') || tLabel('share.none')}`,
    plan.advisorNote ? `${tLabel('share.short')}: ${plan.advisorNote}` : '',
    '',
    tLabel('share.byStore'),
    ...storeSections
  ].filter(Boolean).join('\n');
}

export function formatCurrency(amount: number, market?: string) {
  const config = marketConfig(market ?? activeMarket);
  return new Intl.NumberFormat(config.locale, {
    style: 'currency',
    currency: config.currency,
    maximumFractionDigits: 0
  }).format(amount);
}
