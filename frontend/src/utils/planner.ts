import type { FurnishingLevel, FurnishingPlan, PlannerInput, ProductCategory, Retailer, RoomType, ShoppingPriority, StyleType } from '../types';

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
  const breakdown = plan.items.reduce<Record<string, { total: number; count: number }>>((acc, item) => {
    const retailer = item.product.retailer;
    acc[retailer] ??= { total: 0, count: 0 };
    acc[retailer].total += item.product.price;
    acc[retailer].count += 1;
    return acc;
  }, {});

  return Object.entries(breakdown)
    .map(([retailer, value]) => ({ retailer: retailer as Retailer, ...value }))
    .sort((a, b) => b.total - a.total);
}

export function formatPlanForSharing(plan: FurnishingPlan, input: PlannerInput) {
  const breakdown = getRetailerBreakdown(plan)
    .map((entry) => `${entry.retailer}: ${formatCurrency(entry.total)} (${entry.count} proizvoda)`)
    .join(' | ');
  const byPriority = (priority: string) => plan.items.filter((item) => item.shoppingPriority === priority);
  const formatItems = (items: typeof plan.items) => items.map((item) => `- ${item.product.name} — ${item.product.retailer} — ${formatCurrency(item.product.price)}`);
  const favoriteFirst = byPriority('buy-first');
  const comfort = byPriority('add-comfort');
  const later = byPriority('later');
  const uncategorized = plan.items.filter((item) => !item.shoppingPriority);

  return [
    `Moj BudgetSpace plan: ${roomLabels[input.roomType]}`,
    `Budžet: ${formatCurrency(input.budget)}`,
    `Ukupno: ${formatCurrency(plan.total)}${plan.total <= input.budget ? ` · ostaje ${formatCurrency(input.budget - plan.total)}` : ` · ${formatCurrency(plan.total - input.budget)} iznad budžeta`}`,
    `Trgovine: ${plan.retailersUsed.join(', ') || 'nema'}`,
    breakdown ? `Po trgovinama: ${breakdown}` : '',
    plan.advisorNote ? `Kratko: ${plan.advisorNote}` : '',
    '',
    favoriteFirst.length ? 'NAJVAŽNIJE ZA PLAN' : '',
    ...formatItems(favoriteFirst),
    comfort.length ? '' : '',
    comfort.length ? 'ZA UGODNIJI PROSTOR' : '',
    ...formatItems(comfort),
    later.length ? '' : '',
    later.length ? 'MOŽE KASNIJE' : '',
    ...formatItems(later),
    uncategorized.length ? '' : '',
    ...formatItems(uncategorized)
  ].filter(Boolean).join('\n');
}

export function formatCurrency(amount: number) {
  return new Intl.NumberFormat('hr-HR', {
    style: 'currency',
    currency: 'EUR',
    maximumFractionDigits: 0
  }).format(amount);
}
