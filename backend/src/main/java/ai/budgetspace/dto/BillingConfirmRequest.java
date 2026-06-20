package ai.budgetspace.dto;

/**
 * Sprint 10.69 — body of {@code POST /api/billing/confirm}: the Stripe Checkout Session id from the success
 * redirect, which the backend re-fetches from Stripe to verify payment before upgrading the account.
 */
public record BillingConfirmRequest(String sessionId) {
}
