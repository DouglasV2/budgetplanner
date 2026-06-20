package ai.budgetspace.dto;

/**
 * Sprint 10.68 — body of {@code POST /api/events/plus-interest}. Both fields optional: a bare click is a valid
 * interest signal; an email opts the visitor into the Plus launch list.
 *
 * @param email  optional email for the launch list.
 * @param source where the interest came from (e.g. "pricing", "save-limit").
 */
public record PlusInterestRequest(String email, String source) {
}
