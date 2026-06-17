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
 * Sprint 10.22 (road-to-production step 2) — HR catalog maximization. Web-verified gap-fill across the
 * thin room/category cells (dining-room, home-office, kitchen, hallway, bathroom) plus non-IKEA breadth
 * (Emmezeta / Harvey Norman / Namjestaj.hr) for price/style diversity. Proves the file imports cleanly,
 * every row is planner-eligible (so it actually reaches plans under the verified-only gate), and the
 * targeted gaps are now covered.
 */
class HrMaxCatalogRuntimeTest {

    @Test
    void hrMaxCatalogImportsCleanlyAndFillsGaps() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = load("/catalog/real-hr-max-10-22.json");
        assertThat(snapshot).as("HR-max rows").hasSizeGreaterThanOrEqualTo(50);

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
            assertThat(product.getMarket()).isEqualTo("HR");
            assertThat(product.getRetailer()).isIn("IKEA", "JYSK", "Emmezeta", "Harvey Norman", "Namjestaj.hr");
            assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(product.getProductUrl()).startsWith("https://");
            assertThat(URI.create(product.getProductUrl()).getHost()).isNotBlank();
            // Verified-only gate: every maximized row must be planner-eligible (sourced, in-stock, etc.) —
            // except rows flagged `needs-review` by re-verification (Sprint 10.24 found a dead/redirected
            // URL), which are intentionally held out of the planner until re-sourced.
            if (!"needs-review".equals(product.getDataQuality())) {
                assertThat(CatalogSourcePolicy.isPlannerEligible(product)).as("planner-eligible %s", product.getExternalId()).isTrue();
            }
        });

        // The previously-thin cells are now covered.
        assertThat(saved).anySatisfy(p -> { assertThat(p.getRoomTags()).contains("dining-room"); assertThat(p.getCategory()).isEqualTo("storage"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getRoomTags()).contains("dining-room"); assertThat(p.getCategory()).isEqualTo("lighting"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getRoomTags()).contains("home-office"); assertThat(p.getCategory()).isEqualTo("rug"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getRoomTags()).contains("kitchen"); assertThat(p.getCategory()).isEqualTo("decor"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getRoomTags()).contains("hallway"); assertThat(p.getCategory()).isEqualTo("lighting"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getRoomTags()).contains("bathroom"); assertThat(p.getCategory()).isEqualTo("decor"); });
        // Non-IKEA breadth landed.
        assertThat(saved).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("Harvey Norman"));
        assertThat(saved).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("Namjestaj.hr"));
    }

    private List<RetailerProductSnapshotDto> load(String resource) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertThat(in).as("catalog resource %s", resource).isNotNull();
            return mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }
}
