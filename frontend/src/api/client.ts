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
  // Sprint 10.51: matched second-hand ("Rabljeno") listings — a separate block shown under the new-retail
  // plans, never counted into any plan total. Empty when no marketplace feed is configured or nothing matches.
  secondHandSuggestions?: Product[];
}

// Stable per-browser id so the backend can apply per-session AI usage limits + scope a guest's saved plans.
// No PII. On Google sign-in this id is sent once so the guest's saved plans migrate onto the account.
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

/** The guest browser-session id (used to migrate guest-saved plans onto an account on first sign-in). */
export function getGuestSessionId(): string {
  return sessionId();
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      // Sprint 10.63: send the auth session cookie on every API call so signed-in requests are recognised
      // (CORS allows credentials for the explicit frontend origins). Harmless for guest calls.
      credentials: 'include',
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
    // Attach the HTTP status so callers can react to specific cases (e.g. 402 = Free plan limit → Plus upsell).
    const error = new Error(message || `Zahtjev nije uspio (${response.status}).`) as Error & { status?: number };
    error.status = response.status;
    throw error;
  }

  // A 204 (e.g. logout) has no body — calling .json() on it would throw, so return nothing.
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

function fireAndForget(path: string, payload: unknown) {
  void fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    keepalive: true,
    credentials: 'include',
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

// Sprint 10.78: instant deterministic (rule-based, no AI) plan — shown as a draft while generatePlan refines
// it with AI in the background, so the user sees a plan in ~50ms instead of waiting ~2s for the LLM.
export function generatePlanFast(input: PlannerInput) {
  return request<PlanGenerationResponse>('/api/plans/generate-fast', {
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
    // Sprint 10.71: send the browser session so the design-summary AI is gated per guest, like generate.
    headers: { 'X-BudgetSpace-Session': sessionId() },
    body: JSON.stringify(plan)
  });
}

export function replaceProduct(plan: FurnishingPlan, input: PlannerInput, productId: string, changeType: ReplacementChoice = 'similar') {
  return request<FurnishingPlan>('/api/plans/replace', {
    method: 'POST',
    body: JSON.stringify({ plan, input, productId, changeType })
  });
}

// Sprint 10.53: send the browser session so the backend scopes saved plans to this owner — the "Moji
// planovi" inbox returns only the caller's own plans. getSavedPlan deliberately omits the header: it is the
// shareable link, open by id, so a recipient on another session can still open a plan shared with them.
export function savePlan(plan: FurnishingPlan, input: PlannerInput, spaceName?: string) {
  return request<SavedPlanResponse>('/api/saved-plans', {
    method: 'POST',
    headers: { 'X-BudgetSpace-Session': sessionId() },
    // Sprint 10.61: spaceName groups this room-plan under a "space" (e.g. "Moj dom") in "Moji planovi".
    body: JSON.stringify({ plan, input, spaceName })
  });
}

export function getSavedPlan(id: string) {
  return request<SavedPlanResponse>(`/api/saved-plans/${id}`);
}

export function listSavedPlans() {
  return request<SavedPlanResponse[]>('/api/saved-plans', {
    headers: { 'X-BudgetSpace-Session': sessionId() }
  });
}

export function setSavedPlanFavorite(id: string, favorite: boolean) {
  return request<SavedPlanResponse>(`/api/saved-plans/${id}/favorite`, {
    method: 'PATCH',
    headers: { 'X-BudgetSpace-Session': sessionId() },
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

// Sprint 10.63: Google Sign-In + server sessions. The session lives in an HttpOnly cookie (set by the backend);
// the frontend never sees the token, it just calls /me to learn who is signed in.
export interface AuthUser {
  id: string;
  email: string | null;
  name: string | null;
  pictureUrl: string | null;
  // Sprint 10.68: monetization tier — "FREE" or "PLUS". Drives Plus-only gates + the upsell.
  plan: string | null;
}

export interface AuthMe {
  user: AuthUser | null;
  googleEnabled: boolean;
  // The PUBLIC Google client id, served by the backend so the frontend has one source of truth. Null when
  // Google sign-in is not configured (dormant — guest-only, no fake auth).
  googleClientId: string | null;
  // Sprint 10.69: true when Stripe is configured → the Plus CTA can start checkout (else it's the waitlist).
  billingEnabled: boolean;
  // Sprint 10.89: true when the AI layer is usable → show the "Plus = more AI" nudge only when upgrading would
  // actually unlock more AI (never when AI is off entirely).
  aiEnabled: boolean;
}

/** Who (if anyone) is signed in, plus whether Google sign-in is available and the client id to render it. */
export function fetchAuthMe() {
  return request<AuthMe>('/api/auth/me');
}

/** Exchange a Google ID token for a session; sends the guest id so guest-saved plans migrate onto the account. */
export function googleLogin(credential: string) {
  return request<AuthUser>('/api/auth/google', {
    method: 'POST',
    body: JSON.stringify({ credential, guestSessionId: getGuestSessionId() })
  });
}

/** Sign out: the backend deletes the session and clears the cookie. */
export function authLogout() {
  return request<void>('/api/auth/logout', { method: 'POST' });
}

// Sprint 10.72: GDPR "right to be forgotten" — permanently delete the signed-in account and all its data
// (saved plans + sessions). The backend clears the cookie; the caller then drops the local user.
export function deleteAccount() {
  return request<void>('/api/auth/account', { method: 'DELETE' });
}

// Sprint 10.68: an early willingness-to-pay signal — "I want Plus", optionally with an email for the launch
// list. Fire-and-forget: no payment is taken (a waitlist + interest counter until Stripe is wired).
export function recordPlusInterest(email?: string, source?: string) {
  void fetch(`${API_BASE_URL}/api/events/plus-interest`, {
    method: 'POST',
    keepalive: true,
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', 'X-BudgetSpace-Session': sessionId() },
    body: JSON.stringify({ email: email ?? null, source: source ?? null })
  }).catch(() => undefined);
}

// Sprint 10.69: Stripe checkout. The backend creates a hosted Checkout Session and returns its URL; the frontend
// redirects there. On return (?plus=success&session_id=…) confirmCheckout verifies payment + upgrades the account.
export function startCheckout() {
  return request<{ url: string }>('/api/billing/checkout', { method: 'POST' });
}

export function confirmCheckout(sessionId: string) {
  return request<{ plan: string }>('/api/billing/confirm', {
    method: 'POST',
    body: JSON.stringify({ sessionId })
  });
}
