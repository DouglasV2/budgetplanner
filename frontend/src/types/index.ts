export type Retailer =
  | 'IKEA'
  | 'JYSK'
  | 'Pevex'
  | 'Decathlon'
  | 'Emmezeta'
  | 'Lesnina'
  // Sprint 10.30: retailers that actually have verified products in some markets (mirrors the backend
  // PlannerService.RETAILERS list) so the store picker can offer them per country.
  | 'Harvey Norman'
  | 'Namjestaj.hr'
  | 'Otto'
  | 'Segmüller'
  | 'Poco'
  // Sprint 10.36: France
  | 'Camif'
  // Sprint 10.43: Spain
  | 'Kenay Home'
  | 'Banak Importa'
  // Sprint 10.44: Netherlands
  | 'Leen Bakker'
  | 'Kwantum'
  // Sprint 10.45: depth — Portugal + Slovakia
  | 'Moviflor'
  | 'Nábytok'
  // Sprint 10.48: retail re-sweep — more verified retailers per market
  | 'Svijetnamještaja'
  | 'Svetpohištva'
  | 'Conforama'
  | 'Interio'
  | 'Masku'
  | 'Lovely Meubles'
  | 'JOM'
  | 'Sítio do Móvel'
  | 'Miroytengo'
  | 'Merkamueble'
  | 'Muebles BOOM'
  | 'Pronto Wonen'
  | 'Drevona'
  | 'ASKO Nábytok';

export type RoomType =
  | 'living-room'
  | 'home-office'
  | 'bedroom'
  | 'home-gym'
  | 'kitchen'
  | 'dining-room'
  | 'hallway'
  | 'bathroom';

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
  | 'gym-equipment'
  | 'dining-table'
  | 'dining-chair'
  | 'kitchen-storage'
  | 'kitchen-cart'
  | 'nightstand'
  | 'wardrobe'
  | 'dresser';

export interface Product {
  id: string;
  name: string;
  retailer: Retailer;
  category: ProductCategory;
  price: number;
  originalPrice?: number;
  // Sprint 10.33: discount / sale tracking. A product is "on sale" only when a verified originalPrice
  // (the regular price) is strictly greater than price. saleEndsAt (ISO date) is the verified promo end
  // window (e.g. JYSK priceValidUntil); absent = no known deadline. Never fabricated.
  saleEndsAt?: string;
  styleTags: string[];
  roomTags: RoomType[];
  imageUrl?: string;
  // Sprint 10.23: true only when imageUrl was verified on the retailer's live product page. The UI
  // shows the real photo only then; otherwise it keeps the labelled "ilustracija" category placeholder.
  imageVerified?: boolean;
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
  // Sprint 10.7: optional colour/material tags used for smarter matching.
  colorTags?: string[];
  materialTags?: string[];
  // Sprint 10.10: affiliate/sponsored groundwork (sponsored is always clearly labelled in the UI).
  originalProductUrl?: string;
  affiliateUrl?: string;
  sponsored?: boolean;
  sponsorLabel?: string;
  // Sprint 10.13: reviews (#2) + market/currency (#3).
  reviewCount?: number;
  reviewRating?: number;
  reviewsUrl?: string;
  market?: string;
  currency?: string;
}

// Sprint 10.10: structured understanding of the user's prompt (AI or rule-based).
export interface PlannerIntentAnalysis {
  roomType?: string;
  budget?: number;
  currency?: string;
  roomSize?: number;
  style?: string;
  preferredRetailers?: string[];
  mustHaveCategories?: string[];
  alreadyHaveCategories?: string[];
  avoidCategories?: string[];
  colorPreferences?: string[];
  materialPreferences?: string[];
  qualityPreference?: 'budget' | 'balanced' | 'premium' | string;
  urgency?: string;
  confidence?: number;
  missingImportantInfo?: string[];
  userGoalSummary?: string;
  normalizedPrompt?: string;
  warnings?: string[];
  aiUsed: boolean;
  source?: string;
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
  // Sprint 10.7: colour/material preferences parsed from the prompt (canonical keys, optional).
  colorPreferences?: string[];
  materialPreferences?: string[];
  // Sprint 10.13: market/country (e.g. HR, SI, AT, DE) for catalog + currency.
  market?: string;
}

// Sprint 10.8: short description of a generated plan from the (rule-based) design assistant.
export interface DesignAssistant {
  summary: string;
  highlights: string[];
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
