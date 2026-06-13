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
import ai.budgetspace.product.ProductRepository;
import ai.budgetspace.product.ProductTaxonomy;
import ai.budgetspace.product.RetailerSnapshotImportService;
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
    private static final int MAX_URLS = 20;

    private final ProductPageFetcher fetcher;
    private final RetailerProductParser parser;
    private final RetailerSnapshotImportService snapshotImportService;
    private final ProductRepository productRepository;

    public RetailerCollectorService(ProductPageFetcher fetcher, RetailerProductParser parser,
                                    RetailerSnapshotImportService snapshotImportService, ProductRepository productRepository) {
        this.fetcher = fetcher;
        this.parser = parser;
        this.snapshotImportService = snapshotImportService;
        this.productRepository = productRepository;
    }

    public CollectorRunSummaryDto collect(CollectorRequestDto request) {
        String runId = UUID.randomUUID().toString();
        String startedAt = Instant.now().toString();

        if (request == null) {
            return rejected(runId, startedAt, null, 0, "Nema podataka u zahtjevu.");
        }
        Optional<String> retailerOpt = ProductTaxonomy.normalizeRetailer(request.retailer());
        String retailer = retailerOpt.orElse(request.retailer());
        List<WorkItem> workItems = resolveWorkItems(request);
        int totalReceived = workItems.size();

        if (retailerOpt.isEmpty()) {
            return rejected(runId, startedAt, retailer, totalReceived, "Trgovina nije podržana: " + request.retailer() + ".");
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
            ProductPageFetcher.FetchResult result = fetcher.fetch(url);
            if (!result.ok()) {
                errors.add(new CollectorErrorDto(url, result.error()));
                reports.add(new CollectorProductReportDto(url, null, null, "skipped", result.error(), null, List.of()));
                continue;
            }
            fetched++;

            RetailerProductParser.ParsedProduct parsedProduct;
            try {
                parsedProduct = parser.parse(result.html(), url, retailer, item.defaults());
            } catch (RuntimeException exception) {
                errors.add(new CollectorErrorDto(url, "Stranicu nije bilo moguće pročitati."));
                reports.add(new CollectorProductReportDto(url, null, null, "skipped", "Stranicu nije bilo moguće pročitati.", null, List.of()));
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
                continue;
            }

            boolean existed = notBlank(collected.externalId())
                    && productRepository.findByExternalId(collected.externalId().trim()).isPresent();
            candidates.add(new Candidate(collected, itemWarnings, existed));
            candidateSnapshots.add(collected.toSnapshot());
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
            } else {
                String status = candidate.existed() ? "updated" : "imported";
                reports.add(new CollectorProductReportDto(product.productUrl(), externalId, product.name(), status, "OK", product.dataQuality(), candidate.warnings()));
            }
        }

        int imported = importSummary.created();
        int updated = importSummary.updated();
        int skipped = Math.max(0, totalReceived - imported - updated - needsReview);
        String finishedAt = Instant.now().toString();

        return new CollectorRunSummaryDto(runId, startedAt, finishedAt, retailer, totalReceived,
                fetched, parsed, imported, updated, skipped, needsReview,
                errors, new ArrayList<>(warnings), importSummary, reports, reviewItems);
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
                new ImportSummaryDto(0, 0, 0, 0, List.of(), List.of()), List.of(), List.of());
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

    private record WorkItem(String url, CollectorDefaultsDto defaults) {
    }

    private record Candidate(CollectedProductDto product, List<String> warnings, boolean existed) {
    }
}
