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
 * Sprint 10.14 (go-wide) — proves the verified IKEA Austria (AT) snapshot imports cleanly: real
 * ikea.com/at product URLs, per-market EUR prices (verified on the AT site, not copied from HR/SI),
 * market=AT, and verified review aggregates that are display-only and never fabricated. Second non-HR
 * market catalog after IKEA SI.
 */
class IkeaAustriaCatalogRuntimeTest {
    private static final String RESOURCE = "/catalog/real-ikea-at.json";

    @Test
    void austrianSnapshotImportsCleanlyWithMarketAndReviews() throws Exception {
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
            assertThat(product.getMarket()).isEqualTo("AT");
            assertThat(product.getPrice().signum()).isPositive();
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(URI.create(product.getProductUrl()).getHost()).endsWith("ikea.com");
            assertThat(product.getProductUrl()).contains("/at/");
        });
        // Verified review aggregate present on at least some rows (display-only, separate from rating).
        assertThat(saved).anySatisfy(product -> {
            assertThat(product.getReviewRating()).isNotNull();
            assertThat(product.getReviewRating()).isBetween(1.0, 5.0);
            assertThat(product.getReviewCount()).isNotNull();
            assertThat(product.getReviewCount()).isPositive();
        });
        // Covers more than one room so an AT plan can be built from real products.
        assertThat(saved).anySatisfy(product -> assertThat(product.getRoomTags()).contains("living-room"));
        assertThat(saved).anySatisfy(product -> assertThat(product.getRoomTags()).contains("home-office"));
        // Carries a real sourceReference + verified source type (the non-time-dependent parts of the
        // production-verified gate; freshness is exercised date-safely in CatalogSourcePolicyTest).
        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getSourceReference()).isNotBlank();
            assertThat(CatalogSourcePolicy.isFeedSourceType(product.getSourceType())).isFalse();
            assertThat(ProductTaxonomy.canEnterPlanner(product)).isTrue();
        });
    }

    private List<RetailerProductSnapshotDto> load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(RESOURCE)) {
            assertThat(in).as("catalog resource %s", RESOURCE).isNotNull();
            return mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }
}
