package ai.budgetspace.geo;

/**
 * Sprint 10.42 — the visitor's country (2-letter ISO code) as read from a CDN/proxy header, plus which
 * header it came from (for debugging). Both null when no geo header is present.
 */
public record GeoDto(String country, String source) {
}
