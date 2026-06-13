export type Retailer = 'IKEA' | 'JYSK' | 'Pevex' | 'Decathlon' | 'Emmezeta' | 'Lesnina';

export type RoomType = 'living-room' | 'home-office' | 'bedroom' | 'home-gym';

export type StyleType =
  | 'bright'
  | 'warm'
  | 'modern'
  | 'minimal'
  | 'classic'
  | 'industrial'
  | 'boho'
  | 'surprise'
  | 'scandinavian'
  | 'cozy';

export type RetailerMode = 'single' | 'multi';

export type OptimizationGoal = 'lowest-price' | 'best-value' | 'least-stores' | 'style-match';

export type FurnishingLevel = 'basic' | 'comfort' | 'complete';

export type ShoppingPriority = 'buy-first' | 'add-comfort' | 'later';

export type ReplacementChoice = 'cheaper' | 'nicer' | 'different' | 'remove' | 'similar';

export type AvailabilityStatus = 'in-stock' | 'limited' | 'unavailable' | 'check-store' | 'unknown';

export type PriceTier = 'budget' | 'standard' | 'premium';

export type ProductCategory =
  | 'sofa'
  | 'chair'
  | 'table'
  | 'tv-unit'
  | 'storage'
  | 'rug'
  | 'lighting'
  | 'decor'
  | 'desk'
  | 'bed'
  | 'mattress'
  | 'gym-equipment';

export interface Product {
  id: string;
  name: string;
  retailer: Retailer;
  category: ProductCategory;
  price: number;
  originalPrice?: number;
  styleTags: string[];
  roomTags: RoomType[];
  imageUrl?: string;
  productUrl?: string;
  availabilityStatus?: AvailabilityStatus | string;
  deliveryNote?: string;
  lastCheckedAt?: string;
  externalId?: string;
  retailerProductId?: string;
  priceTier?: PriceTier | string;
  image?: string;
  url?: string;
  rating: number;
  inStock?: boolean;
  note: string;
}

export interface PlannerInput {
  prompt: string;
  budget: number;
  roomType: RoomType;
  style: StyleType;
  location: string;
  size: number;
  retailerMode: RetailerMode;
  selectedRetailers: Retailer[];
  optimizationGoal: OptimizationGoal;
  furnishingLevel: FurnishingLevel;
  mustHaveCategories: ProductCategory[];
  alreadyHaveCategories: ProductCategory[];
  lockedProductIds: string[];
  preferredRetailers?: Retailer[];
  excludedRetailers?: Retailer[];
  maxStores?: number;
}

export interface PlanItem {
  product: Product;
  reason: string;
  shoppingPriority?: ShoppingPriority;
  shoppingRole?: string;
  stepTitle?: string;
}

export interface StoreTotal {
  retailer: Retailer;
  total: number;
  itemCount: number;
}

export interface StoreTrip {
  storeCount: number;
  mainRetailer?: Retailer | null;
  mainRetailerTotal: number;
  checkInStoreCount: number;
  recommendation: string;
  stores: StoreTotal[];
}

export interface FurnishingPlan {
  id: string;
  name: string;
  label: string;
  description: string;
  summary: string;
  goodFor: string;
  tradeoff: string;
  budgetStatus: string;
  advisorNote: string;
  nextStep: string;
  savingTips: string[];
  upgradeTips: string[];
  items: PlanItem[];
  total: number;
  savings: number;
  fitScore: number;
  shoppingEffort: 'Low' | 'Medium' | 'High';
  styleConsistency: number;
  retailersUsed: Retailer[];
  storeTrip?: StoreTrip | null;
  purchaseSummary?: string[] | null;
  budgetRepairSuggestions?: string[] | null;
  overBudgetAmount?: number | null;
  storeLimitNote?: string | null;
}

export interface SavedPlanResponse {
  id: string;
  plan: FurnishingPlan;
  input: PlannerInput;
  createdAt: string;
  favorite: boolean;
}

export type PlanFeedback = 'useful' | 'too-expensive' | 'wrong-style' | 'too-many-stores';
