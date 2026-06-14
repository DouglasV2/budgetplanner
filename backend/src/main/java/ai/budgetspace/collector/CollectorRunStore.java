package ai.budgetspace.collector;

import ai.budgetspace.dto.CollectorRunDetailDto;
import ai.budgetspace.dto.CollectorRunDto;
import ai.budgetspace.dto.CollectorRunItemDto;
import ai.budgetspace.dto.CollectorRunSummaryDto;
import ai.budgetspace.product.CollectorRun;
import ai.budgetspace.product.CollectorRunItem;
import ai.budgetspace.product.CollectorRunItemRepository;
import ai.budgetspace.product.CollectorRunRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Persists collector runs and their per-URL items, and reads them back (Sprint 9.5).
 *
 * <p>Saving is <strong>best-effort</strong>: if the database is unavailable it is swallowed so
 * a DB issue never breaks the collector run itself. This is dev/audit data, not user-facing.</p>
 */
@Service
public class CollectorRunStore {
    private static final String WARN_SEP = " || ";
    private static final String FIELD_SEP = ",";

    private final CollectorRunRepository runRepository;
    private final CollectorRunItemRepository itemRepository;

    public CollectorRunStore(CollectorRunRepository runRepository, CollectorRunItemRepository itemRepository) {
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
    }

    public void save(CollectorRunSummaryDto summary, String requestSummary) {
        if (summary == null || summary.runId() == null) return;
        try {
            CollectorRun run = new CollectorRun();
            run.setId(summary.runId());
            run.setRetailer(summary.retailer());
            run.setStartedAt(summary.startedAt());
            run.setFinishedAt(summary.finishedAt());
            run.setTotalReceived(summary.totalReceived());
            run.setFetched(summary.fetched());
            run.setParsed(summary.parsed());
            run.setImported(summary.imported());
            run.setUpdated(summary.updated());
            run.setSkipped(summary.skipped());
            run.setNeedsReview(summary.needsReview());
            run.setRequestSummary(requestSummary);
            run.setCreatedAt(Instant.now());
            runRepository.save(run);

            Map<String, String> missingByUrl = new HashMap<>();
            if (summary.reviewItems() != null) {
                summary.reviewItems().forEach(review -> {
                    if (review.url() != null) missingByUrl.put(review.url(), join(review.missingFields(), FIELD_SEP));
                });
            }
            if (summary.products() != null) {
                summary.products().forEach(report -> {
                    CollectorRunItem item = new CollectorRunItem();
                    item.setId(UUID.randomUUID().toString());
                    item.setRunId(summary.runId());
                    item.setUrl(report.url());
                    item.setExternalId(report.externalId());
                    item.setName(report.name());
                    item.setStatus(report.status());
                    item.setDataQuality(report.dataQuality());
                    item.setMessage(report.message());
                    item.setWarnings(join(report.warnings(), WARN_SEP));
                    item.setMissingFields(missingByUrl.get(report.url()));
                    itemRepository.save(item);
                });
            }
        } catch (RuntimeException ignored) {
            // Best-effort: persistence must never break the collector run.
        }
    }

    public List<CollectorRunDto> listRecent() {
        try {
            return runRepository.findTop50ByOrderByCreatedAtDesc().stream().map(this::toRunDto).toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    public Optional<CollectorRunDetailDto> detail(String runId) {
        try {
            return runRepository.findById(runId).map(run -> {
                List<CollectorRunItemDto> items = itemRepository.findByRunId(runId).stream().map(this::toItemDto).toList();
                return new CollectorRunDetailDto(toRunDto(run), items);
            });
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private CollectorRunDto toRunDto(CollectorRun run) {
        return new CollectorRunDto(run.getId(), run.getRetailer(), run.getStartedAt(), run.getFinishedAt(),
                run.getTotalReceived(), run.getFetched(), run.getParsed(), run.getImported(), run.getUpdated(),
                run.getSkipped(), run.getNeedsReview(), run.getRequestSummary(), run.getCreatedAt());
    }

    private CollectorRunItemDto toItemDto(CollectorRunItem item) {
        return new CollectorRunItemDto(item.getUrl(), item.getExternalId(), item.getName(), item.getStatus(),
                item.getDataQuality(), item.getMessage(), split(item.getWarnings(), WARN_SEP), split(item.getMissingFields(), FIELD_SEP));
    }

    private String join(List<String> values, String separator) {
        return values == null || values.isEmpty() ? null : String.join(separator, values);
    }

    private List<String> split(String value, String separator) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(Pattern.quote(separator)))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();
    }
}
