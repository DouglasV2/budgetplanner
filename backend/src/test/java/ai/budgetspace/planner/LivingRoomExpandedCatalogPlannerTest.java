package ai.budgetspace.planner;

import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlanItemDto;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductImportService;
import ai.budgetspace.product.ProductRepository;
import ai.budgetspace.product.RetailerCatalogAdapter;
import ai.budgetspace.product.RetailerSnapshotImportService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.5 — a living-room plan is built from the expanded real IKEA/JYSK catalog: every
 * recommended product has a real product URL, unavailable / needs-review products are excluded, and
 * the newly added IKEA armchairs are reachable when the user asks for a chair. No live internet.
 */
class LivingRoomExpandedCatalogPlannerTest {

    private static final List<String> RESOURCES = List.of(
            "/catalog/real-ikea-jysk-hr-living-room.json",
            "/catalog/real-ikea-jysk-hr-living-room-expansion.json");

    @Test
    void livingRoomPlanUsesExpandedRealCatalogWithRealUrls() throws Exception {
        List<Product> products = importedRealProducts();
        products.add(unavailableProduct("ikea-unavailable-sofa"));
        products.add(needsReviewProduct("ikea-review-sofa"));

        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(products);
        PlannerService service = new PlannerService(repository);

        PlanGenerationResponse response = service.generate(plannerInput("Imam 1500 € za dnevni boravak, moderno.", 1500, "comfort", List.of()));
        List<PlanItemDto> items = response.plans().get(0).items();

        assertThat(items).isNotEmpty();
        for (PlanItemDto item : items) {
            assertThat(item.product().retailer()).isIn("IKEA", "JYSK");
            assertThat(item.product().name()).isNotBlank();
            assertThat(item.product().price().signum()).isPositive();
            assertThat(item.product().productUrl()).isNotBlank();
            assertThat(isRealProductUrl(item.product().productUrl()))
                    .as("recommended product has a real product URL: %s", item.product().productUrl())
                    .isTrue();
        }
        assertThat(items).extracting(item -> item.product().id())
                .doesNotContain("ikea-unavailable-sofa", "ikea-review-sofa");
    }

    @Test
    void plannerCanRecommendTheNewlyAddedIkeaArmchairs() throws Exception {
        List<Product> products = importedRealProducts();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(products);
        PlannerService service = new PlannerService(repository);

        // chair is not part of the default living-room flow, so request it explicitly (as the UI
        // does when the user asks for a "fotelja"); the expanded catalog must be able to supply one.
        PlanGenerationResponse response = service.generate(
                plannerInput("Imam 2500 € za dnevni boravak, trebam i fotelju.", 2500, "complete", List.of("chair")));

        List<PlanItemDto> chairItems = response.plans().stream()
                .flatMap(plan -> plan.items().stream())
                .filter(item -> "chair".equals(item.product().category()))
                .toList();
        assertThat(chairItems).as("expanded catalog supplies an armchair when requested").isNotEmpty();
        for (PlanItemDto chair : chairItems) {
            assertThat(chair.product().retailer()).isIn("IKEA", "JYSK");
            assertThat(isRealProductUrl(chair.product().productUrl()))
                    .as("armchair has a real product URL: %s", chair.product().productUrl()).isTrue();
        }
    }

    private List<Product> importedRealProducts() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<RetailerProductSnapshotDto> snapshot = new ArrayList<>();
        for (String resource : RESOURCES) {
            try (InputStream in = getClass().getResourceAsStream(resource)) {
                assertThat(in).as("catalog resource %s", resource).isNotNull();
                snapshot.addAll(mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {}));
            }
        }
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(Optional.empty());
        List<Product> saved = new ArrayList<>();
        when(repository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            saved.add(product);
            return product;
        });
        new RetailerSnapshotImportService(new ProductImportService(repository), new RetailerCatalogAdapter())
                .importSnapshot(snapshot);
        return new ArrayList<>(saved);
    }

    private boolean isRealProductUrl(String url) {
        if (url == null || url.isBlank() || url.startsWith("#")) return false;
        URI uri = URI.create(url);
        String host = uri.getHost();
        String path = uri.getPath();
        boolean okHost = host != null && (host.endsWith("ikea.com") || host.endsWith("ikea.hr") || host.endsWith("jysk.hr"));
        boolean realPath = path != null && path.length() > 1 && !path.equals("/hr/hr/");
        return okHost && realPath;
    }

    private Product unavailableProduct(String id) {
        Product product = baseProduct(id, "Nedostupan kauč");
        product.setAvailabilityStatus("unavailable");
        product.setInStock(false);
        return product;
    }

    private Product needsReviewProduct(String id) {
        Product product = baseProduct(id, "Kauč za pregled");
        product.setDataQuality("needs-review");
        return product;
    }

    private Product baseProduct(String id, String name) {
        Product product = new Product();
        product.setId(id);
        product.setExternalId(id);
        product.setName(name);
        product.setRetailer("IKEA");
        product.setCategory("sofa");
        product.setPrice(BigDecimal.valueOf(300));
        product.setStyleTags("modern,cozy");
        product.setRoomTags("living-room");
        product.setProductUrl("https://www.ikea.com/hr/hr/p/blocked-1/");
        product.setUrl("https://www.ikea.com/hr/hr/p/blocked-1/");
        product.setAvailabilityStatus("in-stock");
        product.setInStock(true);
        product.setLastCheckedAt("2026-06-14");
        product.setSourceReference("ikea-jysk-hr-living-room-production-pilot-10-3");
        product.setRating(4.0);
        return product;
    }

    private PlannerInputDto plannerInput(String prompt, int budget, String furnishingLevel, List<String> mustHave) {
        return new PlannerInputDto(prompt, budget, "living-room", "modern", "Zagreb", 20, "multi",
                List.of("IKEA", "JYSK", "Pevex", "Emmezeta", "Decathlon", "Lesnina"),
                "best-value", furnishingLevel, mustHave, List.of(), List.of(), List.of(), List.of(), 0);
    }
}
