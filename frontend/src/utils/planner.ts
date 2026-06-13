import type { FurnishingLevel, FurnishingPlan, PlanItem, PlannerInput, Product, ProductCategory, Retailer, RoomType, ShoppingPriority, StoreTotal, StoreTrip, StyleType } from '../types';

export const retailers: Retailer[] = ['IKEA', 'JYSK', 'Pevex', 'Emmezeta', 'Decathlon', 'Lesnina'];

export const categoryLabels: Record<ProductCategory, string> = {
  sofa: 'Sofa / kauč',
  chair: 'Stolica',
  table: 'Klub stolić',
  'tv-unit': 'TV komoda',
  storage: 'Polica / spremanje',
  rug: 'Tepih',
  lighting: 'Rasvjeta',
  decor: 'Dekoracije',
  desk: 'Radni stol',
  bed: 'Krevet',
  mattress: 'Madrac',
  'gym-equipment': 'Oprema za vježbanje'
};

export const roomLabels: Record<RoomType, string> = {
  'living-room': 'dnevni boravak',
  'home-office': 'radni kutak',
  bedroom: 'spavaća soba',
  'home-gym': 'kućna teretana'
};

export const furnishingLevelLabels: Record<FurnishingLevel, string> = {
  basic: 'osnovno',
  comfort: 'udobnije',
  complete: 'kompletno'
};

export const shoppingPriorityLabels: Record<ShoppingPriority, string> = {
  'buy-first': 'Najvažnije',
  'add-comfort': 'Za ugodniji prostor',
  later: 'Može kasnije'
};

export const styleLabels: Record<StyleType, string> = {
  bright: 'svijetlo i prozračno',
  warm: 'toplo i domaće',
  modern: 'moderno i uredno',
  minimal: 'jednostavno i čisto',
  classic: 'klasično',
  industrial: 'tamno / industrijski',
  boho: 'boho / prirodno',
  surprise: 'nisam siguran, predloži mi',
  scandinavian: 'svijetlo i prozračno',
  cozy: 'toplo i domaće'
};

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
  const mod100 = count % 100;
  const mod10 = count % 10;
  if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) return 'trgovine';
  return 'trgovina';
}

export function storeCountText(count: number) {
  return `${count} ${storesWord(count)}`;
}

function buildStoreTripRecommendation(storeCount: number, mainRetailer: Retailer | null, checkInStoreCount: number) {
  let base: string;
  if (storeCount <= 0 || !mainRetailer) {
    base = 'Plan je još prazan, dodaj barem jedan glavni komad.';
  } else if (storeCount === 1) {
    base = `Sve kupuješ u ${mainRetailer}, pa je manje obilazaka.`;
  } else {
    base = `Većinu kupuješ u ${mainRetailer}, a plan koristi ${storeCount} ${storesWord(storeCount)}.`;
  }
  if (checkInStoreCount > 0) {
    base += checkInStoreCount === 1 ? ' Jednu stvar prvo provjeri u trgovini.' : ' Neke stvari prvo provjeri u trgovini.';
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
    `${entry.retailer} — ${entry.count} ${entry.count === 1 ? 'stvar' : 'stvari'} — ${formatCurrency(entry.total)}`,
    ...entry.items.map((item) => `- ${item.product.name} — ${formatCurrency(item.product.price)} (${shoppingPriorityLabels[item.shoppingPriority ?? 'later']})`),
    ''
  ]);

  return [
    `Moj BudgetSpace plan: ${roomLabels[input.roomType]}`,
    `Budžet: ${formatCurrency(input.budget)}`,
    `Ukupno: ${formatCurrency(plan.total)}${plan.total <= input.budget ? ` · ostaje ${formatCurrency(input.budget - plan.total)}` : ` · ${formatCurrency(plan.total - input.budget)} iznad budžeta`}`,
    `Trgovine: ${plan.retailersUsed.join(', ') || 'nema'}`,
    plan.advisorNote ? `Kratko: ${plan.advisorNote}` : '',
    '',
    'POPIS PO TRGOVINAMA',
    ...storeSections
  ].filter(Boolean).join('\n');
}

export function formatCurrency(amount: number) {
  return new Intl.NumberFormat('hr-HR', {
    style: 'currency',
    currency: 'EUR',
    maximumFractionDigits: 0
  }).format(amount);
}
