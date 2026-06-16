package ai.budgetspace.product;

import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sprint 10.4 / 10.5 — loads the real IKEA HR + JYSK HR living-room catalog into the runtime
 * {@code products} table on startup, so the planner recommends real products with real product
 * URLs instead of the sample seed rows.
 *
 * <p>Runs after {@code data.sql} (which still seeds non-living-room / other-retailer sample data)
 * and, when enabled:</p>
 * <ol>
 *   <li>imports every real living-room snapshot resource through the existing, validated import
 *       pipeline ({@link RetailerSnapshotImportService}); imports are idempotent because the
 *       pipeline dedupes by {@code externalId} (existing rows are updated, never duplicated), and</li>
 *   <li>retires the {@code living-room} tag from the legacy sample products (which have no
 *       {@code sourceReference}), so a living-room plan is built only from the real catalog — even
 *       if the import itself fails, the fake homepage-link products are never served to users.</li>
 * </ol>
 *
 * <p>The seed is controlled by {@code budgetspace.real-catalog.seed-enabled}
 * (env {@code BUDGETSPACE_REAL_CATALOG_SEED_ENABLED}). It defaults to {@code true} so local/dev
 * runs work out of the box; set it to {@code false} to keep the sample catalog untouched.</p>
 *
 * <p>Best-effort and safe: a failure is logged and the application keeps running.</p>
 */
@Component
@Order(100)
public class RealCatalogSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RealCatalogSeeder.class);
    private static final String LIVING_ROOM = "living-room";

    /** Real catalog resources, imported in order. Add new approved snapshots here. */
    private static final List<String> SNAPSHOT_RESOURCES = List.of(
            "/catalog/real-ikea-jysk-hr-living-room.json",
            "/catalog/real-ikea-jysk-hr-living-room-expansion.json",
            // Sprint 10.7/10.9: verified JYSK HR products for the new rooms (dining-room, kitchen, hallway).
            "/catalog/real-jysk-hr-new-rooms.json",
            // Sprint 10.11: verified IKEA + JYSK HR products covering bedroom, home-office and bathroom.
            "/catalog/real-ikea-jysk-hr-rooms-expansion.json",
            // Sprint 10.12: catalog depth — lighting, rugs, decor, extra chairs/mattress across rooms.
            "/catalog/real-ikea-jysk-hr-depth.json",
            // Sprint 10.13: third HR retailer — verified Emmezeta products (living-room + bedroom).
            "/catalog/real-emmezeta-hr.json",
            // Sprint 10.13 (#3, go-wide): first non-HR market — verified IKEA Slovenia (SI) catalog
            // across living-room, home-office, bedroom and kitchen (EUR prices + real review counts).
            "/catalog/real-ikea-si.json",
            // Sprint 10.14 (go-wide): second EU market — verified IKEA Austria (AT) catalog across
            // living-room, home-office, bedroom and kitchen. Prices verified per-market on ikea.com/at
            // (they genuinely differ from HR/SI), reviews are the near-global aggregate. market="AT".
            "/catalog/real-ikea-at.json",
            // Sprint 10.14 (go-wide): third EU market — verified IKEA Germany (DE) catalog. Prices
            // verified per-market on ikea.com/de (they differ again from HR/SI/AT). market="DE".
            "/catalog/real-ikea-de.json",
            // Sprint 10.15 (production-depth): web-verified catalog depth across retailers and markets
            // (beds, mattresses, wardrobes, dining, office, more living-room) so the rule-based planner
            // has rich data before any LLM spend. Each row's name + EUR price verified on the live
            // public product page; sourceType=public-product-page.
            "/catalog/real-ikea-si-depth.json",
            "/catalog/real-ikea-at-depth.json",
            "/catalog/real-ikea-de-depth.json",
            "/catalog/real-jysk-hr-depth.json",
            "/catalog/real-jysk-si.json",
            "/catalog/real-jysk-at.json",
            "/catalog/real-jysk-de.json",
            "/catalog/real-emmezeta-hr-depth.json",
            // Sprint 10.16: HR kitchen depth + additional verified retailers (Harvey Norman HR/SI,
            // Namjestaj.hr, Otto/Segmüller/Poco DE). Other named retailers (Momax, Bauhaus, FeroTerm,
            // Prima, Perfecta, Merkur, Dipo, Wayfair, Home24, Roller, Kika, Leiner, XXXLutz) are
            // registered as OFFICIAL_FEED_REQUIRED (403/anti-bot/JS-only) and carry no products yet.
            "/catalog/real-hr-kitchen.json",
            "/catalog/real-harvey-norman.json",
            "/catalog/real-namjestaj-hr.json",
            "/catalog/real-de-new-retailers.json"
    );

    private final ProductRepository productRepository;
    private final RetailerSnapshotImportService snapshotImportService;
    private final ObjectMapper objectMapper;
    private final boolean seedEnabled;

    public RealCatalogSeeder(ProductRepository productRepository,
                             RetailerSnapshotImportService snapshotImportService,
                             ObjectMapper objectMapper,
                             @Value("${budgetspace.real-catalog.seed-enabled:true}") boolean seedEnabled) {
        this.productRepository = productRepository;
        this.snapshotImportService = snapshotImportService;
        this.objectMapper = objectMapper;
        this.seedEnabled = seedEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Real catalog seed: disabled via budgetspace.real-catalog.seed-enabled=false; keeping existing catalog as-is.");
            return;
        }
        log.info("Real catalog seed: starting (resources={}).", SNAPSHOT_RESOURCES);
        int importedCount = 0;
        try {
            List<RetailerProductSnapshotDto> snapshot = loadAllSnapshots();
            if (snapshot.isEmpty()) {
                log.warn("Real catalog seed: no snapshot rows found on classpath; living-room will rely on whatever remains after legacy cleanup.");
            } else {
                ImportSummaryDto summary = snapshotImportService.importSnapshot(snapshot);
                importedCount = summary.created() + summary.updated();
                log.info("Real catalog seed: imported real products (received={}, created={}, updated={}, skipped={}).",
                        summary.totalReceived(), summary.created(), summary.updated(), summary.skipped());
                if (!summary.errors().isEmpty()) {
                    log.warn("Real catalog seed: {} snapshot row(s) rejected by validation and not imported.", summary.errors().size());
                }
            }
        } catch (Exception exception) {
            log.error("Real catalog seed: import failed; legacy sample living-room data will still be retired so users are never shown placeholder links.", exception);
        }
        // Always retire fake living-room when real-catalog mode is enabled, even on import failure.
        int retired = retireLegacyLivingRoomProducts();
        log.info("Real catalog seed: retired {} legacy sample living-room product(s).", retired);
        logCatalogSummary();
        log.info("Real catalog seed: done (realProductsImported={}).", importedCount);
    }

    private List<RetailerProductSnapshotDto> loadAllSnapshots() throws Exception {
        List<RetailerProductSnapshotDto> all = new ArrayList<>();
        for (String resource : SNAPSHOT_RESOURCES) {
            List<RetailerProductSnapshotDto> rows = loadSnapshot(resource);
            if (rows.isEmpty()) {
                log.warn("Real catalog seed: resource {} missing or empty; skipping.", resource);
            } else {
                log.info("Real catalog seed: loaded {} row(s) from {}.", rows.size(), resource);
                all.addAll(rows);
            }
        }
        return all;
    }

    private List<RetailerProductSnapshotDto> loadSnapshot(String resource) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) return List.of();
            return objectMapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }

    /**
     * Legacy sample rows (from {@code data.sql}) have no {@code sourceReference}. Remove their
     * {@code living-room} tag so the planner only sees the real imported products for the living
     * room; delete a sample product that has no other room left. Idempotent: a sample product that
     * has already lost its {@code living-room} tag is skipped on subsequent runs.
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

    /** Logs the resulting catalog mix so an operator can confirm the seed worked. */
    private void logCatalogSummary() {
        List<Product> all = productRepository.findAll();
        long ikea = all.stream().filter(product -> "IKEA".equalsIgnoreCase(product.getRetailer())).count();
        long jysk = all.stream().filter(product -> "JYSK".equalsIgnoreCase(product.getRetailer())).count();
        long usableLivingRoom = all.stream()
                .filter(product -> hasLivingRoom(product.getRoomTags()))
                .filter(ProductTaxonomy::canEnterPlanner)
                .count();
        log.info("Real catalog summary: totalProducts={}, IKEA={}, JYSK={}, usableLivingRoom={}.",
                all.size(), ikea, jysk, usableLivingRoom);
        if (usableLivingRoom == 0) {
            log.warn("Real catalog summary: no usable living-room products after seeding; the planner will return an empty/insufficient result for living-room requests.");
        }
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
