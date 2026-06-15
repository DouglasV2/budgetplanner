package ai.budgetspace.product;

import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sprint 10.4 — loads the real IKEA HR + JYSK HR living-room catalog into the runtime
 * {@code products} table on startup, so the planner actually recommends real products with
 * real product URLs instead of the sample seed rows.
 *
 * <p>It runs after {@code data.sql} (which still seeds non-living-room / other-retailer sample
 * data) and:</p>
 * <ol>
 *   <li>imports the real living-room snapshot through the existing, validated import pipeline
 *       ({@link RetailerSnapshotImportService}); the snapshot products carry a
 *       {@code sourceReference}, real {@code productUrl} and source metadata, and</li>
 *   <li>removes the {@code living-room} tag from the legacy sample products (which have no
 *       {@code sourceReference}), so a living-room plan is built only from the real catalog.</li>
 * </ol>
 *
 * <p>Best-effort: a failure here is logged and the application keeps running on the sample
 * seed.</p>
 */
@Component
@Order(100)
public class RealCatalogSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RealCatalogSeeder.class);
    private static final String SNAPSHOT_RESOURCE = "/catalog/real-ikea-jysk-hr-living-room.json";
    private static final String LIVING_ROOM = "living-room";

    private final ProductRepository productRepository;
    private final RetailerSnapshotImportService snapshotImportService;
    private final ObjectMapper objectMapper;

    public RealCatalogSeeder(ProductRepository productRepository,
                             RetailerSnapshotImportService snapshotImportService,
                             ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.snapshotImportService = snapshotImportService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<RetailerProductSnapshotDto> snapshot = loadSnapshot();
            if (snapshot.isEmpty()) {
                log.warn("Real catalog snapshot not found or empty ({}); keeping sample seed only.", SNAPSHOT_RESOURCE);
                return;
            }
            ImportSummaryDto summary = snapshotImportService.importSnapshot(snapshot);
            int retired = retireLegacyLivingRoomProducts();
            log.info("Real catalog seeded: created={}, updated={}, skipped={}, legacy living-room retired={}",
                    summary.created(), summary.updated(), summary.skipped(), retired);
        } catch (Exception exception) {
            log.warn("Real catalog seed failed; the app continues with the sample seed only.", exception);
        }
    }

    private List<RetailerProductSnapshotDto> loadSnapshot() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(SNAPSHOT_RESOURCE)) {
            if (in == null) return List.of();
            return objectMapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }

    /**
     * Legacy sample rows (from {@code data.sql}) have no {@code sourceReference}. Remove their
     * {@code living-room} tag so the planner only sees the real imported products for the
     * living room; delete a sample product that has no other room left.
     *
     * @return how many legacy products were retired or trimmed.
     */
    private int retireLegacyLivingRoomProducts() {
        int affected = 0;
        for (Product product : productRepository.findAll()) {
            if (notBlank(product.getSourceReference())) continue; // real / imported product, keep
            if (!hasLivingRoom(product.getRoomTags())) continue;
            String remaining = stripLivingRoomTag(product.getRoomTags());
            if (remaining.isBlank()) {
                productRepository.delete(product);
            } else {
                product.setRoomTags(remaining);
                productRepository.save(product);
            }
            affected++;
        }
        return affected;
    }

    /** Removes the {@code living-room} tag from a comma-separated room-tag string. */
    static String stripLivingRoomTag(String roomTags) {
        if (roomTags == null || roomTags.isBlank()) return "";
        return Arrays.stream(roomTags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .filter(tag -> !tag.equalsIgnoreCase(LIVING_ROOM))
                .collect(Collectors.joining(","));
    }

    private boolean hasLivingRoom(String roomTags) {
        if (roomTags == null) return false;
        return Arrays.stream(roomTags.split(","))
                .map(String::trim)
                .anyMatch(tag -> tag.equalsIgnoreCase(LIVING_ROOM));
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
