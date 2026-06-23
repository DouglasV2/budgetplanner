package ai.budgetspace.dto;

/**
 * Sprint 10.63 — the response of {@code GET /api/auth/me}: who (if anyone) is signed in, plus whether Google
 * sign-in is available and the PUBLIC client id the frontend needs to render the Google button. Serving the
 * client id here means the frontend does not duplicate it in its own env — one source of truth, the backend.
 *
 * @param user           the signed-in user, or null when the caller is a guest.
 * @param googleEnabled  true when a Google client id is configured (otherwise sign-in is dormant, guest-only).
 * @param googleClientId the public OAuth Web client id for Google Identity Services, or null when not configured.
 * @param billingEnabled Sprint 10.69: true when Stripe is configured (the Plus CTA can checkout; else waitlist).
 * @param aiEnabled      Sprint 10.89: true when the AI layer is usable (enabled + provider + key), so the frontend
 *                       surfaces the "Plus = more AI" nudge only when upgrading would actually unlock more AI.
 * @param betaMode       Sprint 10.105: the free-beta switch for the one-time "Design Session" model (which
 *                       replaces the removed Plus/Pro subscriptions). true → every premium feature is temporarily
 *                       unlocked for FREE and the app shows a beta notice instead of any price/checkout. Flip
 *                       BUDGETSPACE_BETA_MODE=false later, once one-time payments are wired, to gate premium
 *                       behind a purchased Design Session — the frontend already routes feature access through a
 *                       single {@code premiumUnlocked} seam, so enabling payments is a minimal change.
 */
public record AuthMeResponse(AuthUserDto user, boolean googleEnabled, String googleClientId, boolean billingEnabled,
                             boolean aiEnabled, boolean betaMode) {
}
