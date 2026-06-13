package ai.budgetspace.dto;

/**
 * One URL in a collector request that carries its own defaults (Sprint 9.4). This lets a
 * single request mix categories — e.g. a sofa, a TV unit and a rug — without one wrong
 * global category default. When {@code defaults} is missing, the request-level defaults apply.
 */
public record CollectorItemDto(
        String url,
        CollectorDefaultsDto defaults
) {
}
