package ai.budgetspace.dto;

import java.util.List;

/** Read view of one persisted collector run item (Sprint 9.5). */
public record CollectorRunItemDto(
        String url,
        String externalId,
        String name,
        String status,
        String dataQuality,
        String message,
        List<String> warnings,
        List<String> missingFields
) {
}
