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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.29 (EU depth) — proves the IT + FI dining-room gap-fill imports cleanly. Both markets had
 * {@code dining-room=0}; this file ports verified IKEA dining tables + chairs via the global article-number
 * trick (per-market EUR price + verified og:image confirmed on ikea.com). Every row must be planner-eligible
 * so it actually reaches IT/FI dining-room plans, and each market must have at least one table and one chair.
 */
class EuDiningCatalogRuntimeTest {

    @Test
    void euDiningCatalogImportsCleanlyAndFillsItFiDiningGap() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = load("/catalog/real-eu-dining-10-29.json");
        assertThat(snapshot).as("EU dining rows").isNotEmpty();

        List<Product> saved = new ArrayList<>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            saved.add(product);
            return product;
        });

        ImportSummaryDto summary = new RetailerSnapshotImportService(
                new ProductImportService(repository), new RetailerCatalogAdapter()).importSnapshot(snapshot);

        assertThat(summary.errors()).as("rejected rows: %s", summary.errors()).isEmpty();
        assertThat(summary.created()).isEqualTo(snapshot.size());

        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getRetailer()).isEqualTo("IKEA");
            assertThat(product.getMarket()).isIn("IT", "FI");
            assertThat(product.getCategory()).isIn("dining-table", "dining-chair");
            assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
            assertThat(product.getRoomTags()).contains("dining-room");
            assertThat(product.isImageVerified()).as("verified image %s", product.getExternalId()).isTrue();
            assertThat(product.getProductUrl()).startsWith("https://www.ikea.com/");
            assertThat(URI.create(product.getProductUrl()).getHost()).isNotBlank();
            // Verified-only gate: every EU dining row must reach plans.
            assertThat(CatalogSourcePolicy.isPlannerEligible(product)).as("planner-eligible %s", product.getExternalId()).isTrue();
        });

        // Both previously-empty markets now have at least one dining table and one chair.
        for (String market : new String[] {"IT", "FI"}) {
            assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo(market); assertThat(p.getCategory()).isEqualTo("dining-table"); });
            assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo(market); assertThat(p.getCategory()).isEqualTo("dining-chair"); });
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
