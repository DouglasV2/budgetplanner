package ai.budgetspace.product;

import ai.budgetspace.dto.CatalogHealthDto;
import ai.budgetspace.dto.RoomReadinessDto;
import ai.budgetspace.planner.PlannerReadiness;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Computes a dev-only view of how healthy the product catalog is for the planner (Sprint 9.6).
 *
 * <p>"Usable" means exactly what the planner accepts ({@link ProductTaxonomy#canEnterPlanner}):
 * in stock, not unavailable, not marked for review, with room and style tags. Everything else
 * is "blocked". Per-room readiness reuses the centralised {@link PlannerReadiness} rules.</p>
 */
@Service
public class CatalogHealthService {
    private final ProductRepository productRepository;

    public CatalogHealthService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public CatalogHealthDto compute() {
        List<Product> all = productRepository.findAll();
        int total = all.size();
        int usable = 0;
        int blocked = 0;
        int unavailable = 0;
        int needsReview = 0;
        int stale = 0;
        int missingUrl = 0;
        int missingImage = 0;
        Map<String, Integer> byRoom = new LinkedHashMap<>();
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        Map<String, Integer> byRetailer = new LinkedHashMap<>();
        Map<String, Integer> byDataQuality = new LinkedHashMap<>();

        for (Product product : all) {
            boolean isUsable = ProductTaxonomy.canEnterPlanner(product);
            if (isUsable) {
                usable++;
                for (String room : splitCsv(product.getRoomTags())) increment(byRoom, room);
                increment(byCategory, blankToUnknown(product.getCategory()));
                increment(byRetailer, blankToUnknown(product.getRetailer()));
            } else {
                blocked++;
            }
            if ("unavailable".equals(ProductTaxonomy.normalizeAvailability(product.getAvailabilityStatus()))) unavailable++;
            if (isNeedsReview(product)) needsReview++;
            if (ProductTaxonomy.isStale(product.getLastCheckedAt())) stale++;
            if (isBlank(product.getProductUrl())) missingUrl++;
            if (isBlank(product.getImageUrl())) missingImage++;
            increment(byDataQuality, blankToUnknown(product.getDataQuality()));
        }

        List<RoomReadinessDto> readiness = new ArrayList<>();
        for (String room : PlannerReadiness.ROOMS) {
            readiness.add(assessRoom(room, all));
        }

        return new CatalogHealthDto(total, usable, blocked, unavailable, needsReview, stale,
                missingUrl, missingImage, byRoom, byCategory, byRetailer, byDataQuality, readiness);
    }

    private RoomReadinessDto assessRoom(String room, List<Product> all) {
        Map<String, Integer> countByCategory = new LinkedHashMap<>();
        int usableInRoom = 0;
        for (Product product : all) {
            if (!ProductTaxonomy.canEnterPlanner(product)) continue;
            if (!hasTag(product.getRoomTags(), room)) continue;
            usableInRoom++;
            increment(countByCategory, product.getCategory());
        }

        List<String> missingRequired = new ArrayList<>();
        for (String category : PlannerReadiness.requiredCategories(room)) {
            if (countByCategory.getOrDefault(category, 0) == 0) missingRequired.add(category);
        }
        List<String> weak = new ArrayList<>();
        for (String category : PlannerReadiness.recommendedCategories(room)) {
            if (countByCategory.getOrDefault(category, 0) < 2) weak.add(category);
        }
        return new RoomReadinessDto(room, missingRequired.isEmpty(), missingRequired, weak, usableInRoom);
    }

    private boolean isNeedsReview(Product product) {
        String quality = product.getDataQuality();
        return quality != null && "needs-review".equalsIgnoreCase(quality.trim());
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .toList();
    }

    private boolean hasTag(String csv, String tag) {
        if (csv == null || tag == null) return false;
        return splitCsv(csv).contains(tag.trim().toLowerCase(Locale.ROOT));
    }

    private void increment(Map<String, Integer> counts, String key) {
        counts.merge(key, 1, Integer::sum);
    }

    private String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
