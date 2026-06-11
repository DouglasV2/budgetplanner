import type { FurnishingPlan, PlannerInput, Product } from '../types';

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
    throw new Error(message || `API request failed: ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export function generatePlan(input: PlannerInput) {
  return request<PlanGenerationResponse>('/api/plans/generate', {
    method: 'POST',
    body: JSON.stringify(input)
  });
}

export function replaceProduct(plan: FurnishingPlan, input: PlannerInput, productId: string) {
  return request<FurnishingPlan>('/api/plans/replace', {
    method: 'POST',
    body: JSON.stringify({ plan, input, productId })
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
