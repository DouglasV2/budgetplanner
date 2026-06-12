import type { FurnishingPlan, PlanFeedback, PlannerInput, Product, ReplacementChoice, SavedPlanResponse } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export interface PlanGenerationResponse {
  input: PlannerInput;
  plans: FurnishingPlan[];
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options?.headers ?? {})
    },
    ...options
  });

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
    body: JSON.stringify(input)
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
