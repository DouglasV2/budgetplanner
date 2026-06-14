package ai.budgetspace.dto;

import java.util.List;

/** Read view of a persisted collector run with its per-URL items (Sprint 9.5). */
public record CollectorRunDetailDto(
        CollectorRunDto run,
        List<CollectorRunItemDto> items
) {
}
