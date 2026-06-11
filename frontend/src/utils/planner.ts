import type { FurnishingPlan, PlannerInput, ProductCategory, Retailer, RoomType, StyleType } from '../types';

export const retailers: Retailer[] = ['IKEA', 'JYSK', 'Pevex', 'Emmezeta', 'Decathlon'];

export const categoryLabels: Record<ProductCategory, string> = {
  sofa: 'Sofa / kauč',
  chair: 'Stolica',
  table: 'Klub stolić',
  'tv-unit': 'TV komoda',
  storage: 'Storage / polica',
  rug: 'Tepih',
  lighting: 'Rasvjeta',
  decor: 'Dekoracije',
  desk: 'Radni stol',
  bed: 'Krevet',
  mattress: 'Madrac',
  'gym-equipment': 'Gym oprema'
};

export const roomLabels: Record<RoomType, string> = {
  'living-room': 'dnevni boravak',
  'home-office': 'home office',
  bedroom: 'spavaća soba',
  'home-gym': 'kućna teretana'
};

export const styleLabels: Record<StyleType, string> = {
  scandinavian: 'skandinavski',
  modern: 'moderni',
  minimal: 'minimalistički',
  cozy: 'cozy / ugodni',
  industrial: 'industrijski'
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
  const lines = plan.items.map((item) => `- ${item.product.name} (${item.product.retailer}) — ${formatCurrency(item.product.price)}`);

  return [
    `BudgetSpace AI plan: ${plan.name}`,
    `Prostorija: ${roomLabels[input.roomType]}, stil: ${styleLabels[input.style]}, budžet: ${formatCurrency(input.budget)}`,
    `Ukupno: ${formatCurrency(plan.total)} | Fit score: ${plan.fitScore}% | Trgovine: ${plan.retailersUsed.join(', ') || 'n/a'}`,
    breakdown ? `Breakdown: ${breakdown}` : '',
    '',
    ...lines
  ].filter(Boolean).join('\n');
}

export function formatCurrency(amount: number) {
  return new Intl.NumberFormat('hr-HR', {
    style: 'currency',
    currency: 'EUR',
    maximumFractionDigits: 0
  }).format(amount);
}
