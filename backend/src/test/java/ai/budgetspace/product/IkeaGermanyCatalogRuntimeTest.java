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
 * Sprint 10.14 (go-wide) — proves the verified IKEA Germany (DE) snapshot imports cleanly: real
 * ikea.com/de product URLs, per-market EUR prices (verified on the DE site, not copied from HR/SI/AT),
 * market=DE, and verified review aggregates that are display-only. Third non-HR market catalog.
 */
class IkeaGermanyCatalogRuntimeTest {
    private static final String RESOURCE = "/catalog/real-ikea-de.json";

    @Test
    void germanSnapshotImportsCleanlyWithMarketAndReviews() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = load();
        List<Product> saved = new ArrayList<>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(java.util.Optional.empty());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            saved.add(product);
            return product;
        });

        ImportSummaryDto summary = new RetailerSnapshotImportService(
                new ProductImportService(repository), new RetailerCatalogAdapter()).importSnapshot(snapshot);

        assertThat(summary.errors()).as("import errors").isEmpty();
        assertThat(saved).hasSizeGreaterThanOrEqualTo(10);
        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getRetailer()).isEqualTo("IKEA");
            assertThat(product.getMarket()).isEqualTo("DE");
            assertThat(product.getPrice().signum()).isPositive();
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(URI.create(product.getProductUrl()).getHost()).endsWith("ikea.com");
            assertThat(product.getProductUrl()).contains("/de/");
            assertThat(product.getSourceReference()).isNotBlank();
            assertThat(ProductTaxonomy.canEnterPlanner(product)).isTrue();
        });
        assertThat(saved).anySatisfy(product -> {
            assertThat(product.getReviewRating()).isNotNull();
            assertThat(product.getReviewRating()).isBetween(1.0, 5.0);
            assertThat(product.getReviewCount()).isNotNull();
            assertThat(product.getReviewCount()).isPositive();
        });
        // Covers living-room + home-office so a DE plan can be built from real products.
        assertThat(saved).anySatisfy(product -> assertThat(product.getRoomTags()).contains("living-room"));
        assertThat(saved).anySatisfy(product -> assertThat(product.getRoomTags()).contains("home-office"));
    }

    private List<RetailerProductSnapshotDto> load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(RESOURCE)) {
            assertThat(in).as("catalog resource %s", RESOURCE).isNotNull();
            return mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }
}
