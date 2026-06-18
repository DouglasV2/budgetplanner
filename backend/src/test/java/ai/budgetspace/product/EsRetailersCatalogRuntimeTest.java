package ai.budgetspace.product;

import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.43 — Spain depth: non-IKEA retailers. Kenay Home + Banak Importa serve the price in static HTML
 * (JSON-LD / visible €) on product pages, so they are web-verifiable per product (sourced here); Muebles La
 * Fábrica's product pages reset the connection (anti-bot) so it is {@code OFFICIAL_FEED_REQUIRED}. Proves the
 * Spanish-retailer catalog imports cleanly, every row is market=ES / Kenay-or-Banak / planner-eligible, with
 * the honest current price only (no fabricated discount), and spans the core categories.
 */
class EsRetailersCatalogRuntimeTest {

    @Test
    void spanishRetailerCatalogImportsCleanlyAcrossCategories() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = load("/catalog/real-es-retailers.json");
        assertThat(snapshot).as("ES non-IKEA rows").hasSizeGreaterThanOrEqualTo(20);

        List<Product> saved = new ArrayList<>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenAnswer(inv -> saved.stream()
                .filter(p -> p.getExternalId().equals(inv.getArgument(0))).findFirst());
        when(repository.save(any(Product.class))).thenAnswer(inv -> { saved.add(inv.getArgument(0)); return inv.getArgument(0); });

        ImportSummaryDto summary = new RetailerSnapshotImportService(
                new ProductImportService(repository), new RetailerCatalogAdapter()).importSnapshot(snapshot);

        assertThat(summary.errors()).as("rejected rows: %s", summary.errors()).isEmpty();
        assertThat(summary.created()).isEqualTo(snapshot.size());

        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getMarket()).isEqualTo("ES");
            assertThat(product.getRetailer()).isIn("Kenay Home", "Banak Importa");
            assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(product.getProductUrl()).startsWith("https://");
            assertThat(URI.create(product.getProductUrl()).getHost()).isNotBlank();
            assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                    .as("planner-eligible %s", product.getExternalId()).isTrue();
            // Honest current price only — no phantom discount.
            assertThat(product.getOriginalPrice()).as("no originalPrice %s", product.getExternalId()).isNull();
            // When an image was verified, it must actually carry the URL.
            if (product.isImageVerified()) {
                assertThat(product.getImageUrl()).as("imageUrl %s", product.getExternalId()).isNotBlank();
            }
        });

        // Most rows ship a verified product image (that's how they were sourced).
        long withImage = saved.stream().filter(Product::isImageVerified).count();
        assertThat(withImage).as("rows with a verified image").isGreaterThanOrEqualTo(20);

        // Both verified retailers present; the blocked one is feed-required.
        assertThat(saved).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("Kenay Home"));
        assertThat(saved).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("Banak Importa"));
        assertThat(CatalogSourcePolicy.isFeedRequired("Kenay Home")).isFalse();
        assertThat(CatalogSourcePolicy.isFeedRequired("Banak Importa")).isFalse();
        assertThat(CatalogSourcePolicy.isFeedRequired("Muebles La Fabrica")).isTrue();

        for (String category : List.of("sofa", "table", "dining-table", "storage")) {
            assertThat(saved).as("ES retailers cover %s", category)
                    .anySatisfy(p -> assertThat(p.getCategory()).isEqualTo(category));
        }
    }

    private List<RetailerProductSnapshotDto> load(String resource) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertThat(in).as("catalog resource %s", resource).isNotNull();
            return mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }
}
