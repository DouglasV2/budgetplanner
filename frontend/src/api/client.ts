import type { DesignAssistant, FurnishingPlan, PlanFeedback, PlannerInput, PlannerIntentAnalysis, Product, ReplacementChoice, SavedPlanResponse } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export interface PlanGenerationResponse {
  input: PlannerInput;
  plans: FurnishingPlan[];
  partialPlan?: boolean;
  missingImportantCategories?: string[];
  catalogWarning?: string | null;
  // Sprint 10.10: how the prompt was understood (AI or rule-based).
  intentAnalysis?: PlannerIntentAnalysis | null;
}

// Stable per-browser id so the backend can apply per-session AI usage limits. No PII.
function sessionId(): string {
  try {
    const key = 'bs-session-id';
    let value = localStorage.getItem(key);
    if (!value) {
      value = crypto?.randomUUID?.() ?? `s-${Date.now()}-${Math.random().toString(36).slice(2)}`;
      localStorage.setItem(key, value);
    }
    return value;
  } catch {
    return 'anonymous';
  }
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      // Spread options FIRST, then headers — otherwise an options.headers (e.g. the session header on
      // /api/plans/generate) overwrites the merged headers and drops Content-Type, so the browser sends
      // text/plain and the backend rejects it (HttpMediaTypeNotSupportedException → 500).
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(options?.headers ?? {})
      }
    });
  } catch {
    // Network/CORS failure: the backend is unreachable rather than returning an error status.
    throw new Error('Backend trenutno nije dostupan. Provjeri je li server pokrenut i pokušaj ponovno.');
  }

  if (!response.ok) {
    const message = await response.text().catch(() => '');
    throw new Error(message || `Zahtjev nije uspio (${response.status}).`);
  }

  return response.json() as Promise<T>;
}

function fireAndForget(path: string, payload: unknown) {
  void fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    keepalive: true,
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  }).catch(() => undefined);
}

export function generatePlan(input: PlannerInput) {
  return request<PlanGenerationResponse>('/api/plans/generate', {
    method: 'POST',
    headers: { 'X-BudgetSpace-Session': sessionId() },
    body: JSON.stringify(input)
  });
}

// Sprint 10.42: geo-IP market hint. Reads the visitor's country (2-letter ISO) that a CDN/proxy injected
// as a request header; null when none is present (local/no-CDN). Best-effort — never throws.
export async function fetchGeoCountry(): Promise<string | null> {
  try {
    const res = await request<{ country: string | null }>('/api/geo');
    return res.country ?? null;
  } catch {
    return null;
  }
}

// Sprint 10.34: opt-in price-drop watch. The backend stores only the email + product + threshold (with
// explicit consent) and emails the user when the price falls; one-click unsubscribe in every alert.
export interface PriceWatchResponse {
  id: string;
  productName: string;
  email: string;
  thresholdPercent: number;
  baselinePrice: number;
  active: boolean;
  alreadyWatching: boolean;
  createdAt: string;
}

export function watchProduct(params: {
  email: string;
  externalId: string;
  market?: string;
  thresholdPercent?: number;
  consent: boolean;
}) {
  return request<PriceWatchResponse>('/api/price-watch', {
    method: 'POST',
    headers: { 'X-BudgetSpace-Session': sessionId() },
    body: JSON.stringify(params)
  });
}

// Sprint 10.8: ask the design assistant to describe a freshly generated plan. The frontend first
// calls generatePlan, then passes the whole response here.
export function getDesignSummary(plan: PlanGenerationResponse) {
  return request<DesignAssistant>('/api/plans/design', {
    method: 'POST',
    body: JSON.stringify(plan)
  });
}

export function replaceProduct(plan: FurnishingPlan, input: PlannerInput, productId: string, changeType: ReplacementChoice = 'similar') {
  return request<FurnishingPlan>('/api/plans/replace', {
    method: 'POST',
    body: JSON.stringify({ plan, input, productId, changeType })
  });
}

export function savePlan(plan: FurnishingPlan, input: PlannerInput) {
  return request<SavedPlanResponse>('/api/saved-plans', {
    method: 'POST',
    body: JSON.stringify({ plan, input })
  });
}

export function getSavedPlan(id: string) {
  return request<SavedPlanResponse>(`/api/saved-plans/${id}`);
}

export function listSavedPlans() {
  return request<SavedPlanResponse[]>('/api/saved-plans');
}

export function setSavedPlanFavorite(id: string, favorite: boolean) {
  return request<SavedPlanResponse>(`/api/saved-plans/${id}/favorite`, {
    method: 'PATCH',
    body: JSON.stringify({ favorite })
  });
}

export function trackProductClick(planId: string, product: Product) {
  fireAndForget('/api/events/product-click', {
    planId,
    productId: product.id,
    retailer: product.retailer,
    source: 'plan-card'
  });
}

export function sendPlanFeedback(planId: string, feedback: PlanFeedback, note = '') {
  return request<{ status: string }>('/api/events/plan-feedback', {
    method: 'POST',
    body: JSON.stringify({ planId, feedback, note })
  });
}

export function getProducts(params: Partial<{ retailer: string; category: string; roomType: string; maxPrice: number }> = {}) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value));
  });
  const query = search.toString();
  return request<Product[]>(`/api/products${query ? `?${query}` : ''}`);
}
