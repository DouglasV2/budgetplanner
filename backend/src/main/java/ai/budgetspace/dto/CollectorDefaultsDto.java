package ai.budgetspace.dto;

import java.util.List;

/**
 * Manual fallbacks supplied with a collector request. A real product page rarely states
 * its planner category / room / style in a machine-readable way, so the caller provides
 * sensible defaults that are used when the page cannot tell us.
 */
public record CollectorDefaultsDto(
        String category,
        List<String> roomTags,
        List<String> styleTags,
        String sourceReference
) {
}
