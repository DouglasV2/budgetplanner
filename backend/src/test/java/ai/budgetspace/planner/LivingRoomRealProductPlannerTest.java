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
 * Sprint 10.4 — proves a living-room plan is built from the real IKEA/JYSK catalog, every
 * recommended product carries a real product URL, and unavailable / needs-review products do
 * not enter the plan. The catalog is the real snapshot loaded at runtime; no live internet.
 */
class LivingRoomRealProductPlannerTest {

    @Test
    void livingRoomPlanUsesRealIkeaJyskProductsWithRealUrls() throws Exception {
        List<Product> products = importedRealProducts();
        products.add(unavailableProduct("ikea-unavailable-sofa"));
        products.add(needsReviewProduct("ikea-review-sofa"));

        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(products);
        PlannerService service = new PlannerService(repository);

        PlanGenerationResponse response = service.generate(
                input("Imam 1500 € za dnevni boravak, moderno, najviše IKEA."));
        List<PlanItemDto> items = response.plans().get(0).items();

        assertThat(items).isNotEmpty();
        for (PlanItemDto item : items) {
            assertThat(item.product().retailer()).isIn("IKEA", "JYSK");
            assertThat(item.product().productUrl()).isNotBlank();
            assertThat(isRealProductUrl(item.product().productUrl()))
                    .as("recommended product has a real product URL: %s", item.product().productUrl())
                    .isTrue();
        }
        assertThat(items).extracting(item -> item.product().id())
                .doesNotContain("ikea-unavailable-sofa", "ikea-review-sofa");
    }

    private List<Product> importedRealProducts() throws Exception {
        List<RetailerProductSnapshotDto> snapshot;
        try (InputStream in = getClass().getResourceAsStream("/catalog/real-ikea-jysk-hr-living-room.json")) {
            assertThat(in).as("real catalog snapshot resource").isNotNull();
            snapshot = new ObjectMapper().readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
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
        if (url == null) return false;
        URI uri = URI.create(url);
        String host = uri.getHost();
        String path = uri.getPath();
        boolean okHost = "www.ikea.com".equals(host) || "ikea.com".equals(host) || "jysk.hr".equals(host) || "www.jysk.hr".equals(host);
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
        product.setImage("");
        product.setUrl("https://www.ikea.com/hr/hr/p/blocked-1/");
        product.setImageUrl("");
        product.setProductUrl("https://www.ikea.com/hr/hr/p/blocked-1/");
        product.setAvailabilityStatus("in-stock");
        product.setInStock(true);
        product.setDeliveryNote("Provjeri prije kupnje.");
        product.setLastCheckedAt("2026-06-14");
        product.setPriceTier("standard");
        product.setSourceReference("ikea-jysk-hr-living-room-production-pilot-10-3");
        product.setRating(4.0);
        product.setNote("");
        return product;
    }

    private PlannerInputDto input(String prompt) {
        return new PlannerInputDto(prompt, 1500, "living-room", "modern", "Zagreb", 20, "multi",
                List.of("IKEA", "JYSK", "Pevex", "Emmezeta", "Decathlon", "Lesnina"),
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0);
    }
}
