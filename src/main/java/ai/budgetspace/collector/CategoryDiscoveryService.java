package ai.budgetspace.collector;

import ai.budgetspace.dto.CollectorDefaultsDto;
import ai.budgetspace.dto.CollectorItemDto;
import ai.budgetspace.dto.CollectorRequestDto;
import ai.budgetspace.dto.CollectorRunSummaryDto;
import ai.budgetspace.dto.DiscoveryRequestDto;
import ai.budgetspace.dto.DiscoveryResponseDto;
import ai.budgetspace.product.ProductTaxonomy;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Dev-only service to extract product URLs from a single category or listing page. It is
 * deliberately limited: only one page is fetched, no pagination or search results are
 * followed, and the number of returned URLs is capped. If requested, the discovered
 * products can be immediately fed into the existing collector pipeline.
 */
@Service
public class CategoryDiscoveryService {
    private static final int MAX_LIMIT = 20;

    private final ProductPageFetcher fetcher;
    private final RetailerCollectorService retailerCollectorService;

    public CategoryDiscoveryService(ProductPageFetcher fetcher, RetailerCollectorService retailerCollectorService) {
        this.fetcher = fetcher;
        this.retailerCollectorService = retailerCollectorService;
    }

    public DiscoveryResponseDto discover(DiscoveryRequestDto request) {
        String runId = UUID.randomUUID().toString();
        List<String> found = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Zahtjev je prazan.");
            return new DiscoveryResponseDto(runId, null, null, List.of(), 0, List.of(), List.of(), errors, null);
        }

        String retailer = request.retailer();
        Optional<String> retailerOpt = ProductTaxonomy.normalizeRetailer(retailer);
        if (retailerOpt.isEmpty()) {
            errors.add("Trgovina nije podržana: " + retailer + ".");
            return new DiscoveryResponseDto(runId, retailer, request.categoryUrl(), List.of(), request.limit(), List.of(), List.of(), errors, null);
        }
        retailer = retailerOpt.get();

        String categoryUrl = request.categoryUrl();
        if (categoryUrl == null || categoryUrl.isBlank()) {
            errors.add("categoryUrl je obavezan.");
            return new DiscoveryResponseDto(runId, retailer, categoryUrl, List.of(), request.limit(), List.of(), List.of(), errors, null);
        }
        // Validate limit
        int limit = request.limit() != null && request.limit() > 0 ? Math.min(request.limit(), MAX_LIMIT) : 10;

        // Validate domain using the collector's allowlist
        if (!retailerCollectorServiceAllowedDomain(categoryUrl, retailer)) {
            errors.add("Domena nije podržana za " + retailer + ".");
            return new DiscoveryResponseDto(runId, retailer, categoryUrl, List.of(), limit, List.of(), List.of(), errors, null);
        }

        ProductPageFetcher.FetchResult result = fetcher.fetch(categoryUrl);
        if (!result.ok()) {
            errors.add(result.error());
            return new DiscoveryResponseDto(runId, retailer, categoryUrl, List.of(), limit, List.of(), List.of(), errors, null);
        }
        String html = result.html();
        if (html == null) {
            errors.add("Prazan odgovor sa stranice.");
            return new DiscoveryResponseDto(runId, retailer, categoryUrl, List.of(), limit, List.of(), List.of(), errors, null);
        }

        // Extract product links (anchors) containing "/p/". Use a LinkedHashSet to preserve
        // insertion order and deduplicate.
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("href\\s*=\\s*\"([^\"]+)\"", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
        while (matcher.find() && candidates.size() < limit) {
            String href = matcher.group(1);
            if (href == null || href.isBlank()) continue;
            // Ignore javascript or hash-only links
            String lower = href.toLowerCase(Locale.ROOT);
            if (lower.startsWith("javascript:") || lower.startsWith("#")) continue;
            try {
                URI base = URI.create(categoryUrl);
                URI resolved = base.resolve(href);
                String candidateUrl = resolved.toString();
                // Keep only URLs that appear to be product pages (simple heuristic: contains "/p/")
                if (!candidateUrl.toLowerCase(Locale.ROOT).contains("/p/")) continue;
                // Enforce domain allowlist
                if (!retailerCollectorServiceAllowedDomain(candidateUrl, retailer)) {
                    skipped.add(candidateUrl);
                    continue;
                }
                candidates.add(candidateUrl);
            } catch (RuntimeException ignore) {
                // Ignore malformed href
                skipped.add(href);
            }
        }
        found.addAll(candidates);

        CollectorRunSummaryDto collectorSummary = null;
        boolean shouldCollect = request.collect() != null && request.collect();
        if (shouldCollect && !found.isEmpty()) {
            // Build a collector request using per-item defaults so each discovered URL inherits
            // the caller's defaults. Use items format to allow future per-link overrides.
            List<CollectorItemDto> items = new ArrayList<>();
            CollectorDefaultsDto defaults = request.defaults();
            for (String url : found) {
                items.add(new CollectorItemDto(url, defaults));
            }
            CollectorRequestDto collectorRequest = new CollectorRequestDto(retailer, null, null, items);
            collectorSummary = retailerCollectorService.collect(collectorRequest);
        }

        return new DiscoveryResponseDto(
                runId,
                retailer,
                categoryUrl,
                found,
                limit,
                skipped,
                warnings,
                errors,
                collectorSummary
        );
    }

    /**
     * Delegate to the collector's allowlist check by using reflection to call its private
     * method. Since {@link RetailerCollectorService#isAllowedDomain(String, String)} is
     * private, reflection is used here to reuse the same logic. If reflection fails, this
     * method falls back to a simple host comparison that rejects everything.
     */
    @SuppressWarnings("unchecked")
    private boolean retailerCollectorServiceAllowedDomain(String url, String retailer) {
        try {
            java.lang.reflect.Method method = RetailerCollectorService.class.getDeclaredMethod("isAllowedDomain", String.class, String.class);
            method.setAccessible(true);
            Object result = method.invoke(retailerCollectorService, url, retailer);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            return false;
        }
    }
}