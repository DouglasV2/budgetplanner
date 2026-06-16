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
 * Sprint 10.13 (#3, go-wide) — proves the verified IKEA Slovenia (SI) snapshot imports cleanly:
 * real ikea.com/si URLs, EUR prices, market=SI, and verified review aggregates (rating + count)
 * that are display-only and never fabricated. This is the first non-HR market catalog.
 */
class IkeaSloveniaCatalogRuntimeTest {
    private static final String RESOURCE = "/catalog/real-ikea-si.json";

    @Test
    void slovenianSnapshotImportsCleanlyWithMarketAndReviews() throws Exception {
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
        assertThat(saved).isNotEmpty();
        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getRetailer()).isEqualTo("IKEA");
            assertThat(product.getMarket()).isEqualTo("SI");
            assertThat(product.getPrice().signum()).isPositive();
            assertThat(URI.create(product.getProductUrl()).getHost()).endsWith("ikea.com");
            assertThat(product.getProductUrl()).contains("/si/sl/");
        });
        // At least some products carry a verified review aggregate (rating + count), display-only.
        assertThat(saved).anySatisfy(product -> {
            assertThat(product.getReviewRating()).isNotNull();
            assertThat(product.getReviewRating()).isBetween(1.0, 5.0);
            assertThat(product.getReviewCount()).isNotNull();
            assertThat(product.getReviewCount()).isPositive();
        });
        // Covers more than one room so an SI plan can be built from real products.
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
