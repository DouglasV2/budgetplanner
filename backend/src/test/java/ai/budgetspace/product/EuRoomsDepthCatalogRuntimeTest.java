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
 * Sprint 10.18 — SI/AT/DE depth for the rooms those markets lacked (bathroom, hallway, kitchen). The
 * verified HR IKEA SKUs were ported to each market via IKEA's number-based URL redirect, with the EUR
 * price re-verified per market (prices genuinely differ; never copied across markets). Proves the three
 * market files import cleanly through the validated pipeline (zero rejected rows, real product URLs,
 * {@code public-product-page} provenance) and that each market now has the bathroom/hallway/kitchen
 * coverage the planner needs.
 */
class EuRoomsDepthCatalogRuntimeTest {

    private static final List<String> RESOURCES = List.of(
            "/catalog/real-ikea-si-rooms.json",
            "/catalog/real-ikea-at-rooms.json",
            "/catalog/real-ikea-de-rooms.json");

    @Test
    void euRoomsCatalogsImportCleanlyWithPerMarketPrices() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = loadAll();
        assertThat(snapshot).as("total SI/AT/DE room rows").hasSizeGreaterThanOrEqualTo(95);

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
            assertThat(product.getMarket()).isIn("SI", "AT", "DE");
            assertThat(product.getPrice().signum()).as("price > 0 for %s", product.getExternalId()).isPositive();
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(product.getProductUrl()).as("real URL for %s", product.getExternalId()).startsWith("https://");
            assertThat(URI.create(product.getProductUrl()).getHost()).as("host for %s", product.getExternalId()).isNotBlank();
            assertThat(ProductTaxonomy.canEnterPlanner(product)).as("usable %s", product.getExternalId()).isTrue();
        });

        // Each of the three markets now covers the previously-empty rooms.
        for (String market : List.of("SI", "AT", "DE")) {
            assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo(market); assertThat(p.getRoomTags()).contains("bathroom"); assertThat(p.getCategory()).isEqualTo("storage"); });
            assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo(market); assertThat(p.getRoomTags()).contains("hallway"); });
            assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo(market); assertThat(p.getRoomTags()).contains("kitchen"); });
        }
        // Kitchen carts exist across the EU markets (the headline gap this sprint filled).
        assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo("DE"); assertThat(p.getCategory()).isEqualTo("kitchen-cart"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo("SI"); assertThat(p.getCategory()).isEqualTo("kitchen-cart"); });
    }

    private List<RetailerProductSnapshotDto> loadAll() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<RetailerProductSnapshotDto> all = new ArrayList<>();
        for (String resource : RESOURCES) {
            try (InputStream in = getClass().getResourceAsStream(resource)) {
                assertThat(in).as("catalog resource %s", resource).isNotNull();
                all.addAll(mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {}));
            }
        }
        return all;
    }
}
