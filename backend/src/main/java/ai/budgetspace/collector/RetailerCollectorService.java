package ai.budgetspace.collector;

import ai.budgetspace.dto.CollectedProductDto;
import ai.budgetspace.dto.CollectorErrorDto;
import ai.budgetspace.dto.CollectorRequestDto;
import ai.budgetspace.dto.CollectorRunSummaryDto;
import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.product.ProductTaxonomy;
import ai.budgetspace.product.RetailerSnapshotImportService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 9.1 — first controlled retailer collector.
 *
 * <p>Takes a small, explicit list of product URLs, fetches each one (sequentially), tries to
 * read basic product data, and feeds the results into the existing real-catalog import
 * pipeline (validation + {@code externalId} de-duplication from Sprint 9.0). It is
 * <strong>not</strong> a crawler: no categories, no search, no pagination, no browser.</p>
 *
 * <p>If one URL fails (network, parse, or missing price), it is recorded as an error and the
 * run continues with the rest.</p>
 */
@Service
public class RetailerCollectorService {
    private static final int MAX_URLS = 20;

    private final ProductPageFetcher fetcher;
    private final RetailerProductParser parser;
    private final RetailerSnapshotImportService snapshotImportService;

    public RetailerCollectorService(ProductPageFetcher fetcher, RetailerProductParser parser, RetailerSnapshotImportService snapshotImportService) {
        this.fetcher = fetcher;
        this.parser = parser;
        this.snapshotImportService = snapshotImportService;
    }

    public CollectorRunSummaryDto collect(CollectorRequestDto request) {
        if (request == null || request.urls() == null || request.urls().isEmpty()) {
            return rejected(0, "Nema URL-ova za prikupljanje.");
        }
        int totalReceived = request.urls().size();

        Optional<String> retailer = ProductTaxonomy.normalizeRetailer(request.retailer());
        if (retailer.isEmpty()) {
            return rejected(totalReceived, "Trgovina nije podržana: " + request.retailer() + ".");
        }
        if (totalReceived > MAX_URLS) {
            return rejected(totalReceived, "Najviše " + MAX_URLS + " URL-ova po zahtjevu.");
        }

        List<CollectorErrorDto> errors = new ArrayList<>();
        List<RetailerProductSnapshotDto> snapshots = new ArrayList<>();

        for (String url : request.urls()) {
            if (!looksLikeUrl(url)) {
                errors.add(new CollectorErrorDto(url, "URL ne izgleda ispravno."));
                continue;
            }
            ProductPageFetcher.FetchResult result = fetcher.fetch(url);
            if (!result.ok()) {
                errors.add(new CollectorErrorDto(url, result.error()));
                continue;
            }

            CollectedProductDto collected;
            try {
                collected = parser.parse(result.html(), url, retailer.get(), request.defaults());
            } catch (RuntimeException exception) {
                errors.add(new CollectorErrorDto(url, "Stranicu nije bilo moguće pročitati."));
                continue;
            }

            String validationError = validate(collected);
            if (validationError != null) {
                errors.add(new CollectorErrorDto(url, validationError));
                continue;
            }
            snapshots.add(collected.toSnapshot());
        }

        ImportSummaryDto importSummary = snapshotImportService.importSnapshot(snapshots);
        int imported = importSummary.created() + importSummary.updated();
        int skipped = totalReceived - imported;
        return new CollectorRunSummaryDto(totalReceived, snapshots.size(), imported, skipped, errors, importSummary);
    }

    private String validate(CollectedProductDto collected) {
        if (collected.price() == null) {
            return "Nije pronađena cijena — proizvod nije uvezen.";
        }
        if (isBlank(collected.category())) {
            return "Nedostaje kategorija — dodaj defaults.category u zahtjev.";
        }
        if (collected.roomTags() == null || collected.roomTags().stream().noneMatch(this::notBlank)) {
            return "Nedostaju prostorije — dodaj defaults.roomTags u zahtjev.";
        }
        if (collected.styleTags() == null || collected.styleTags().stream().noneMatch(this::notBlank)) {
            return "Nedostaju stilovi — dodaj defaults.styleTags u zahtjev.";
        }
        return null;
    }

    private CollectorRunSummaryDto rejected(int totalReceived, String message) {
        return new CollectorRunSummaryDto(
                totalReceived, 0, 0, totalReceived,
                List.of(new CollectorErrorDto(null, message)),
                new ImportSummaryDto(0, 0, 0, 0, List.of(), List.of()));
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
