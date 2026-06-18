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
 * Sprint 10.44 — Netherlands depth: non-IKEA/JYSK retailers. Leen Bakker + Kwantum serve the price in static
 * HTML (JSON-LD / visible €) on product pages, so they are web-verifiable per product. Proves the catalog
 * imports cleanly, every row is market=NL / Leen-Bakker-or-Kwantum / planner-eligible, with the honest current
 * price only (no fabricated discount), and spans the core categories.
 */
class NlRetailersCatalogRuntimeTest {

    @Test
    void dutchRetailerCatalogImportsCleanlyAcrossCategories() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = load("/catalog/real-nl-retailers.json");
        assertThat(snapshot).as("NL non-IKEA rows").hasSizeGreaterThanOrEqualTo(20);

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
            assertThat(product.getMarket()).isEqualTo("NL");
            assertThat(product.getRetailer()).isIn("Leen Bakker", "Kwantum");
            assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(product.getProductUrl()).startsWith("https://");
            assertThat(URI.create(product.getProductUrl()).getHost()).isNotBlank();
            assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                    .as("planner-eligible %s", product.getExternalId()).isTrue();
            assertThat(product.getOriginalPrice()).as("no originalPrice %s", product.getExternalId()).isNull();
            if (product.isImageVerified()) {
                assertThat(product.getImageUrl()).as("imageUrl %s", product.getExternalId()).isNotBlank();
            }
        });

        long withImage = saved.stream().filter(Product::isImageVerified).count();
        assertThat(withImage).as("rows with a verified image").isGreaterThanOrEqualTo(15);

        assertThat(saved).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("Leen Bakker"));
        assertThat(saved).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("Kwantum"));
        assertThat(CatalogSourcePolicy.isFeedRequired("Leen Bakker")).isFalse();
        assertThat(CatalogSourcePolicy.isFeedRequired("Kwantum")).isFalse();

        for (String category : List.of("sofa", "table", "dining-table", "storage")) {
            assertThat(saved).as("NL retailers cover %s", category)
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
