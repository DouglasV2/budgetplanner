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
 * @param plusEnabled    Sprint 10.103: master switch for the whole Plus/pricing surface. false during the free
 *                       beta hides the pricing section + in-app upsells (re-enable later by flipping the env flag;
 *                       no code change). Independent of billingEnabled (which only picks checkout vs. waitlist).
 */
public record AuthMeResponse(AuthUserDto user, boolean googleEnabled, String googleClientId, boolean billingEnabled,
                             boolean aiEnabled, boolean plusEnabled) {
}
