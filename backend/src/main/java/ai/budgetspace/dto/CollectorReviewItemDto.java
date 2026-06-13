package ai.budgetspace.dto;

import java.util.List;

/**
 * A URL that was read but is not good enough to import yet. Tells the developer exactly what
 * is missing and what to put into {@code defaults} before repeating the request.
 */
public record CollectorReviewItemDto(
        String url,
        String suggestedExternalId,
        String suggestedName,
        List<String> missingFields,
        CollectorDefaultsDto suggestedDefaults,
        String message
) {
}
