package ai.budgetspace.dto;

/**
 * Sprint 10.63 — the body of {@code POST /api/auth/google}.
 *
 * @param credential       the Google ID token (a JWT) returned by Google Identity Services in the browser.
 * @param guestSessionId   the visitor's pre-login browser session id (the {@code X-BudgetSpace-Session} value),
 *                         so the plans they saved as a guest can be migrated onto the account on first sign-in.
 *                         Optional — null/blank simply means there is nothing to migrate.
 */
public record GoogleLoginRequest(String credential, String guestSessionId) {
}
