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
  image: string;
  url: string;
  rating: number;
  inStock: boolean;
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
}

export interface PlanItem {
  product: Product;
  reason: string;
  shoppingPriority?: ShoppingPriority;
  shoppingRole?: string;
  stepTitle?: string;
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
}

export interface SavedPlanResponse {
  id: string;
  plan: FurnishingPlan;
  input: PlannerInput;
  createdAt: string;
  favorite: boolean;
}

export type PlanFeedback = 'useful' | 'too-expensive' | 'wrong-style' | 'too-many-stores';
