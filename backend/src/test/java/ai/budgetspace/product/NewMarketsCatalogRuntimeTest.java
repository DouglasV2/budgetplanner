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
 * Sprint 10.20 — first catalog for two new EUR markets: Italy (IT) and Finland (FI). IKEA core + room
 * SKUs were ported via the number-trick to ikea.com/it and ikea.com/fi (per-market EUR prices re-verified,
 * never copied), plus JYSK FI hallway/kitchen from jysk.fi. Proves all three files import cleanly through
 * the validated pipeline (zero rejected rows, real product URLs, {@code public-product-page} provenance)
 * and that each new market has usable coverage across the main rooms. Markets.java + frontend markets.ts
 * already list IT/FI (EUR); this fills their previously-empty catalog. Non-EUR EU markets stay deferred.
 */
class NewMarketsCatalogRuntimeTest {

    private static final List<String> RESOURCES = List.of(
            "/catalog/real-ikea-it-rooms.json",
            "/catalog/real-ikea-fi-rooms.json",
            "/catalog/real-jysk-fi-rooms.json");

    @Test
    void newMarketCatalogsImportCleanly() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = loadAll();
        assertThat(snapshot).as("total IT/FI rows").hasSizeGreaterThanOrEqualTo(105);

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
            assertThat(product.getRetailer()).isIn("IKEA", "JYSK");
            assertThat(product.getMarket()).isIn("IT", "FI");
            assertThat(product.getPrice().signum()).as("price > 0 for %s", product.getExternalId()).isPositive();
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(product.getProductUrl()).as("real URL for %s", product.getExternalId()).startsWith("https://");
            assertThat(URI.create(product.getProductUrl()).getHost()).as("host for %s", product.getExternalId()).isNotBlank();
            assertThat(ProductTaxonomy.canEnterPlanner(product)).as("usable %s", product.getExternalId()).isTrue();
        });

        // Both new markets cover the main rooms (living-room core + bedroom + bathroom + kitchen).
        assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo("IT"); assertThat(p.getCategory()).isEqualTo("sofa"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo("FI"); assertThat(p.getCategory()).isEqualTo("sofa"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo("IT"); assertThat(p.getRoomTags()).contains("bathroom"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo("FI"); assertThat(p.getRoomTags()).contains("bathroom"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo("IT"); assertThat(p.getRoomTags()).contains("kitchen"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo("FI"); assertThat(p.getRoomTags()).contains("kitchen"); });
        // JYSK contributed to FI (hallway).
        assertThat(saved).anySatisfy(p -> { assertThat(p.getRetailer()).isEqualTo("JYSK"); assertThat(p.getMarket()).isEqualTo("FI"); assertThat(p.getRoomTags()).contains("hallway"); });
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
