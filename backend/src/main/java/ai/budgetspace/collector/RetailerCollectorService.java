package ai.budgetspace.collector;

import ai.budgetspace.dto.CollectedProductDto;
import ai.budgetspace.dto.CollectorDefaultsDto;
import ai.budgetspace.dto.CollectorErrorDto;
import ai.budgetspace.dto.CollectorItemDto;
import ai.budgetspace.dto.CollectorProductReportDto;
import ai.budgetspace.dto.CollectorRequestDto;
import ai.budgetspace.dto.CollectorReviewItemDto;
import ai.budgetspace.dto.CollectorRunSummaryDto;
import ai.budgetspace.dto.ImportErrorDto;
import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.product.CatalogSourcePolicy;
import ai.budgetspace.product.ProductRepository;
import ai.budgetspace.product.ProductTaxonomy;
import ai.budgetspace.product.RetailerSnapshotImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sprint 9.2/9.4 — controlled retailer collector with a clear run report.
 *
 * <p>Takes a small, explicit list of product URLs (either {@code urls} + global defaults or
 * per-URL {@code items}), fetches each one, reads basic product data, and feeds usable ones
 * into the existing real-catalog import pipeline (validation + {@code externalId} dedup). It
 * is <strong>not</strong> a crawler: no categories, no search, no pagination, no browser.</p>
 *
 * <p>The run summary says exactly what was fetched, parsed, imported, updated, skipped and
 * left for review, plus warnings — so a developer can fix defaults and repeat the request.</p>
 */
@Service
public class RetailerCollectorService {
    private static final Logger log = LoggerFactory.getLogger(RetailerCollectorService.class);
    private static final int MAX_URLS = 20;

    /**
     * Allowlist of host names per supported retailer. Only URLs whose host matches one of
     * these entries (or is a subdomain of one of them) will be fetched. This prevents the
     * collector from wandering onto unrelated domains when given malformed or malicious URLs.
     *
     * <p>The list includes example domains used in tests and docs so placeholder URLs still
     * pass validation. Real pilot runs should replace the placeholders with real retailer
     * domains.</p>
     */
    private static final java.util.Map<String, java.util.List<String>> RETAILER_DOMAINS = java.util.Map.of(
            "IKEA", java.util.List.of(
                    "ikea.com", "www.ikea.com", "ikea.hr", "www.ikea.hr",
                    "example-ikea.com", "www.example-ikea.com"
            ),
            "JYSK", java.util.List.of(
                    "jysk.com", "www.jysk.com", "jysk.hr", "www.jysk.hr",
                    "example.com", "www.example.com"
            )
    );

    /**
     * Milliseconds to wait between HTTP requests. A small pause helps avoid overloading the
     * retailer's servers when fetching a handful of product pages. Do not reduce this
     * without good reason.
     */
    private static final long FETCH_DELAY_MS = 500L;

    private final ProductPageFetcher fetcher;
    private final RetailerProductParser parser;
    private final RetailerSnapshotImportService snapshotImportService;
    private final ProductRepository productRepository;
    private final CollectorRunStore runStore;

    public RetailerCollectorService(ProductPageFetcher fetcher, RetailerProductParser parser,
                                    RetailerSnapshotImportService snapshotImportService, ProductRepository productRepository,
                                    CollectorRunStore runStore) {
        this.fetcher = fetcher;
        this.parser = parser;
        this.snapshotImportService = snapshotImportService;
        this.productRepository = productRepository;
        this.runStore = runStore;
    }

    public CollectorRunSummaryDto collect(CollectorRequestDto request) {
        String runId = UUID.randomUUID().toString();
        String startedAt = Instant.now().toString();

        if (request == null) {
            return rejected(runId, startedAt, null, 0, "Nema podataka u zahtjevu.");
        }
        Optional<String> retailerOpt = ProductTaxonomy.normalizeRetailer(request.retailer());
        String retailer = retailerOpt.orElse(request.retailer());

        if (request.items() != null && request.items().stream().anyMatch(item -> item == null || isBlank(item.url()))) {
            return rejected(runId, startedAt, retailer, request.items().size(), "Svaki item mora imati url.");
        }

        List<WorkItem> workItems = resolveWorkItems(request);
        int totalReceived = workItems.size();

        if (retailerOpt.isEmpty()) {
            return rejected(runId, startedAt, retailer, totalReceived, "Trgovina nije podržana: " + request.retailer() + ".");
        }
        // Sprint 10.14: a feed-required retailer (403/WAF-blocked) must never be scraped/collected.
        // We do not bypass the block — we refuse the run up front with a clear reason and log it.
        if (CatalogSourcePolicy.isFeedRequired(retailer)) {
            String message = retailer + " zahtijeva službeni feed (OFFICIAL_FEED_REQUIRED): " + CatalogSourcePolicy.reasonFor(retailer);
            log.warn("Collector: odbijam direktno prikupljanje za feed-required trgovinu {} (run {}). {}", retailer, runId, message);
            return rejected(runId, startedAt, retailer, totalReceived, message);
        }
        if (workItems.isEmpty()) {
            return rejected(runId, startedAt, retailer, 0, "Nema URL-ova za prikupljanje.");
        }
        if (totalReceived > MAX_URLS) {
            return rejected(runId, startedAt, retailer, totalReceived, "Najviše " + MAX_URLS + " URL-ova po zahtjevu.");
        }

        List<CollectorErrorDto> errors = new ArrayList<>();
        List<CollectorProductReportDto> reports = new ArrayList<>();
        List<CollectorReviewItemDto> reviewItems = new ArrayList<>();
        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        List<RetailerProductSnapshotDto> candidateSnapshots = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        List<CollectorItemDto> retryItems = new ArrayList<>();

        int fetched = 0;
        int parsed = 0;
        int needsReview = 0;

        for (WorkItem item : workItems) {
            String url = item.url();
            if (!looksLikeUrl(url)) {
                errors.add(new CollectorErrorDto(url, "URL ne izgleda ispravno."));
                reports.add(new CollectorProductReportDto(url, null, null, "skipped", "URL ne izgleda ispravno.", null, List.of()));
                continue;
            }

            // Enforce a retailer-specific domain allowlist. If the URL's host does not match
            // one of the allowed domains for the retailer, skip it. This prevents the
            // collector from venturing onto unknown domains such as link shorteners or
            // third-party sites. A host is considered valid if it matches exactly or is a
            // subdomain of an allowed entry. Example: "store.ikea.com" is accepted for
            // "ikea.com".
            if (!isAllowedDomain(url, retailer)) {
                String message = "Domena nije podržana za " + retailer + ".";
                errors.add(new CollectorErrorDto(url, message));
                reports.add(new CollectorProductReportDto(url, null, null, "skipped", message, null, List.of()));
                retryItems.add(new CollectorItemDto(url, item.defaults()));
                continue;
            }

            ProductPageFetcher.FetchResult result = fetcher.fetch(url);
            if (!result.ok()) {
                errors.add(new CollectorErrorDto(url, result.error()));
                reports.add(new CollectorProductReportDto(url, null, null, "skipped", result.error(), null, List.of()));
                retryItems.add(new CollectorItemDto(url, item.defaults()));
                continue;
            }
            fetched++;

            RetailerProductParser.ParsedProduct parsedProduct;
            try {
                parsedProduct = parser.parse(result.html(), url, retailer, item.defaults());
            } catch (RuntimeException exception) {
                errors.add(new CollectorErrorDto(url, "Stranicu nije bilo moguće pročitati."));
                reports.add(new CollectorProductReportDto(url, null, null, "skipped", "Stranicu nije bilo moguće pročitati.", null, List.of()));
                retryItems.add(new CollectorItemDto(url, item.defaults()));
                continue;
            }
            parsed++;

            CollectedProductDto collected = parsedProduct.product();
            List<String> itemWarnings = parsedProduct.warnings();
            warnings.addAll(itemWarnings);

            List<String> missing = missingFields(collected);
            if (!missing.isEmpty()) {
                needsReview++;
                String message = "Nedostaje: " + String.join(", ", missing) + ". Dopuni defaults i ponovi zahtjev.";
                reviewItems.add(new CollectorReviewItemDto(url, collected.externalId(), collected.name(), missing, item.defaults(), message));
                reports.add(new CollectorProductReportDto(url, collected.externalId(), collected.name(), "needs-review", message, collected.dataQuality(), itemWarnings));
                retryItems.add(new CollectorItemDto(url, item.defaults()));
                continue;
            }

            boolean existed = notBlank(collected.externalId())
                    && productRepository.findByExternalId(collected.externalId().trim()).isPresent();
            candidates.add(new Candidate(collected, itemWarnings, existed, item.defaults()));
            candidateSnapshots.add(collected.toSnapshot());

            // Respect a brief delay between requests to avoid hammering the retailer. Even
            // though the loop is sequential, the delay helps spread out network calls. If
            // the thread is interrupted, propagate the interrupt status and continue.
            try {
                Thread.sleep(FETCH_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        ImportSummaryDto importSummary = snapshotImportService.importSnapshot(candidateSnapshots);
        Set<String> importErrorIds = importSummary.errors().stream()
                .map(ImportErrorDto::externalId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Candidate candidate : candidates) {
            CollectedProductDto product = candidate.product();
            String externalId = product.externalId() == null ? null : product.externalId().trim();
            if (externalId != null && importErrorIds.contains(externalId)) {
                String message = importSummary.errors().stream()
                        .filter(error -> externalId.equals(error.externalId()))
                        .map(ImportErrorDto::message)
                        .findFirst()
                        .orElse("Preskočeno u importu.");
                errors.add(new CollectorErrorDto(product.productUrl(), message));
                reports.add(new CollectorProductReportDto(product.productUrl(), externalId, product.name(), "skipped", message, product.dataQuality(), candidate.warnings()));
                retryItems.add(new CollectorItemDto(product.productUrl(), candidate.defaults()));
            } else {
                String status = candidate.existed() ? "updated" : "imported";
                reports.add(new CollectorProductReportDto(product.productUrl(), externalId, product.name(), status, "OK", product.dataQuality(), candidate.warnings()));
            }
        }

        int imported = importSummary.created();
        int updated = importSummary.updated();
        int skipped = Math.max(0, totalReceived - imported - updated - needsReview);
        String finishedAt = Instant.now().toString();

        CollectorRequestDto retryRequest = retryItems.isEmpty()
                ? null
                : new CollectorRequestDto(retailer, null, null, retryItems);

        CollectorRunSummaryDto summary = new CollectorRunSummaryDto(runId, startedAt, finishedAt, retailer, totalReceived,
                fetched, parsed, imported, updated, skipped, needsReview,
                errors, new ArrayList<>(warnings), importSummary, reports, reviewItems, retryRequest);
        runStore.save(summary, requestSummary(request, retailer));
        return summary;
    }

    private String requestSummary(CollectorRequestDto request, String retailer) {
        if (request.items() != null && !request.items().isEmpty()) {
            return "retailer=" + retailer + ", items=" + request.items().size();
        }
        int urlCount = request.urls() == null ? 0 : request.urls().size();
        return "retailer=" + retailer + ", urls=" + urlCount;
    }

    private List<WorkItem> resolveWorkItems(CollectorRequestDto request) {
        List<WorkItem> work = new ArrayList<>();
        if (request.items() != null && !request.items().isEmpty()) {
            for (CollectorItemDto item : request.items()) {
                if (item == null || isBlank(item.url())) continue;
                work.add(new WorkItem(item.url().trim(), mergeDefaults(item.defaults(), request.defaults())));
            }
            return work;
        }
        if (request.urls() != null) {
            for (String url : request.urls()) {
                if (isBlank(url)) continue;
                work.add(new WorkItem(url.trim(), request.defaults()));
            }
        }
        return work;
    }

    // Per-item defaults win; anything they leave out falls back to the request-level defaults.
    private CollectorDefaultsDto mergeDefaults(CollectorDefaultsDto item, CollectorDefaultsDto global) {
        if (item == null) return global;
        if (global == null) return item;
        return new CollectorDefaultsDto(
                firstNonBlank(item.category(), global.category()),
                hasItems(item.roomTags()) ? item.roomTags() : global.roomTags(),
                hasItems(item.styleTags()) ? item.styleTags() : global.styleTags(),
                firstNonBlank(item.sourceReference(), global.sourceReference())
        );
    }

    private List<String> missingFields(CollectedProductDto collected) {
        List<String> missing = new ArrayList<>();
        if (collected.price() == null) missing.add("price");
        if (isBlank(collected.name())) missing.add("name");
        if (isBlank(collected.category())) missing.add("category");
        if (!hasItems(collected.roomTags())) missing.add("roomTags");
        if (!hasItems(collected.styleTags())) missing.add("styleTags");
        if (isBlank(collected.productUrl())) missing.add("productUrl");
        return missing;
    }

    private CollectorRunSummaryDto rejected(String runId, String startedAt, String retailer, int totalReceived, String message) {
        String now = Instant.now().toString();
        return new CollectorRunSummaryDto(runId, startedAt, now, retailer, totalReceived,
                0, 0, 0, 0, totalReceived, 0,
                List.of(new CollectorErrorDto(null, message)), List.of(),
                new ImportSummaryDto(0, 0, 0, 0, List.of(), List.of()), List.of(), List.of(), null);
    }

    private boolean looksLikeUrl(String value) {
        if (isBlank(value)) return false;
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && notBlank(uri.getHost());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String firstNonBlank(String a, String b) {
        return notBlank(a) ? a : b;
    }

    private boolean hasItems(List<String> values) {
        return values != null && values.stream().anyMatch(this::notBlank);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Returns {@code true} if the given URL's host belongs to the allowlist for the
     * specified retailer. Hosts that match exactly or are subdomains of an allowed entry
     * are accepted. Unknown retailers or malformed URLs are rejected. This helper
     * intentionally ignores scheme and port differences.
     */
    private boolean isAllowedDomain(String url, String retailer) {
        if (url == null || retailer == null) return false;
        java.util.List<String> allowed = RETAILER_DOMAINS.get(retailer);
        if (allowed == null || allowed.isEmpty()) return false;
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            String host = uri.getHost();
            if (host == null) return false;
            String hostLower = host.toLowerCase(java.util.Locale.ROOT);
            for (String allowedHost : allowed) {
                String allowedLower = allowedHost.toLowerCase(java.util.Locale.ROOT);
                if (hostLower.equals(allowedLower) || hostLower.endsWith("." + allowedLower)) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private record WorkItem(String url, CollectorDefaultsDto defaults) {
    }

    private record Candidate(CollectedProductDto product, List<String> warnings, boolean existed, CollectorDefaultsDto defaults) {
    }
}
