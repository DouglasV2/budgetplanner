export type Retailer =
  | 'IKEA'
  | 'JYSK'
  | 'Pevex'
  | 'Decathlon'
  | 'Emmezeta'
  | 'Lesnina'
  // Sprint 10.169: bathroom-fixture (sanitary-ware) retailers.
  | 'VVS Eksperten'
  // Sprint 10.178: GB sanitary-ware specialist (toilets/baths/showers).
  | 'Victorian Plumbing'
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
  | 'bathroom'
  // Sprint 10.121: studio / one-room flat (combined living + bedroom).
  | 'studio';

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

// Sprint 10.183 (Move-In QoL): how soon the user needs a room done. Drives the whole-apartment budget split
// (see backend MoveInRequestDto.roomPriority) — not a decorative label. 'soon' is the neutral default.
export type RoomPriority = 'now' | 'soon' | 'later';

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
  // Sprint 10.175: a complete/modular kitchen unit (KNOXHULT/ENHET-class), sold as one priced product.
  | 'kitchen-set'
  // Sprint 10.176: kitchen appliances (added to the plan only when the user asks for them).
  | 'oven'
  | 'hob'
  | 'cooker-hood'
  | 'fridge'
  | 'freezer'
  | 'dishwasher'
  | 'microwave'
  | 'nightstand'
  | 'wardrobe'
  | 'dresser'
  | 'textiles'
  // Sprint 10.169: bathroom fixtures (Pevex HR).
  | 'toilet'
  | 'washbasin'
  | 'bath-shower';

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
  // Sprint 10.51: second-hand marketplace fields (docs/marketplace-sourcing.md §3). secondHand=true marks a
  // used listing shown in the separate "Rabljeno" block — never counted into a plan/budget total.
  // conditionLabel = the seller's stated condition; sellerLocation = city/region for pickup distance.
  secondHand?: boolean;
  conditionLabel?: string;
  sellerLocation?: string;
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
  // Bug 2026-07-10: true when roomType was INFERRED (from a prior prompt written back into the form, or the
  // default), not deliberately chosen. When true the backend lets a new prompt re-derive the room; a false
  // (explicit picker/template selection) is honored over the prompt. See Planner/PlannerForm for how it's set.
  roomInferred?: boolean;
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
  // Sprint 10.120: how many of this product the plan includes (e.g. 6 dining chairs). Line total = price * quantity.
  quantity?: number;
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
  // Sprint 10.61: the "space" (e.g. "Moj dom") this plan belongs to — groups a home's rooms in "Moji planovi".
  spaceName?: string;
}

// Sprint 10.109 (Move-In): the whole-apartment response — one entry per room (its allocated budget + the
// normal 3 plan tiers) plus the grand total and an honest "budget too low" signal.
export interface MoveInRoomPlan {
  roomType: RoomType;
  allocatedBudget: number;
  plans: FurnishingPlan[];
  partial: boolean;
  // Sprint 10.183: optional honesty buckets for the apartment status overview (category keys; may be absent
  // on plans generated before this field existed — treat undefined as empty).
  missingEssential?: string[];
  niceToHave?: string[];
  unavailableInMarket?: string[];
}

export interface MoveInApiResponse {
  rooms: MoveInRoomPlan[];
  grandTotal: number;
  totalBudget: number;
  apartmentPartial: boolean;
  shortfall: number;
  // Sprint 10.183 (adjust apartment): false when an adjust action found nothing useful to do; `message` is a
  // stable CODE the frontend maps to localized honest copy. Absent on a fresh generation.
  changed?: boolean;
  message?: string | null;
}

export type PlanFeedback = 'useful' | 'too-expensive' | 'wrong-style' | 'too-many-stores';

// Sprint 10.175 (kitchen Increment 1): the "complete kitchen" result section. `sets` are real modular kitchen
// sets (each a Product, category 'kitchen-set'); shape/includeAppliances are display-only "understanding";
// showModularNote drives the honest "modular, not fitted" note. Empty `sets` = an honest "no set fits" state.
export interface CompleteKitchen {
  sets: Product[];
  shape: string;
  includeAppliances: boolean;
  showModularNote: boolean;
}
