package ai.budgetspace.dto;

/**
 * Sprint 10.63 — the signed-in user's public profile, returned to the frontend. Deliberately minimal: no
 * tokens, no Google sub, only what the UI shows (name + avatar). The session itself lives in an HttpOnly cookie.
 * Sprint 10.68: {@code plan} ("FREE"/"PLUS") so the UI can gate Plus-only features and show the right upsell.
 */
public record AuthUserDto(String id, String email, String name, String pictureUrl, String plan) {
}
