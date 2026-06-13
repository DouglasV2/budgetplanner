package ai.budgetspace.dto;

/** One URL that could not be collected, with a human-readable reason. */
public record CollectorErrorDto(
        String url,
        String message
) {
}
