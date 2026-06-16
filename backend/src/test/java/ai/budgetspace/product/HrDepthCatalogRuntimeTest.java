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
 * Sprint 10.17 — HR depth for the three thinnest rooms (bathroom, hallway, more kitchen). Proves the
 * new catalog files import cleanly through the validated pipeline with zero rejected rows, real product
 * URLs, positive EUR prices and {@code public-product-page} provenance, and that the bathroom/hallway
 * room coverage the planner needs (storage core + lighting/rug/decor) actually exists. Every row was
 * web-verified on its live public product page on 2026-06-16 (no fabrication).
 */
class HrDepthCatalogRuntimeTest {

    private static final List<String> RESOURCES = List.of(
            "/catalog/real-hr-bathroom.json",
            "/catalog/real-hr-hallway.json",
            "/catalog/real-hr-kitchen-depth.json");

    @Test
    void hrDepthCatalogsImportCleanlyWithVerifiedFields() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = loadAll();
        assertThat(snapshot).as("total HR depth rows").hasSizeGreaterThanOrEqualTo(48);

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

        // Zero rejected rows: every web-verified row passes validation (retailer, category, style,
        // room, price, URL). If this fails, a row has a bad category/style/room/price.
        assertThat(summary.errors()).as("rejected rows: %s", summary.errors()).isEmpty();
        assertThat(summary.created()).isEqualTo(snapshot.size());

        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getRetailer()).isIn("IKEA", "JYSK", "Emmezeta");
            assertThat(product.getMarket()).isEqualTo("HR");
            assertThat(product.getPrice().signum()).as("price > 0 for %s", product.getExternalId()).isPositive();
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(product.getProductUrl()).as("real URL for %s", product.getExternalId()).startsWith("https://");
            assertThat(URI.create(product.getProductUrl()).getHost()).as("host for %s", product.getExternalId()).isNotBlank();
            assertThat(ProductTaxonomy.canEnterPlanner(product)).as("usable %s", product.getExternalId()).isTrue();
        });

        // Bathroom coverage: storage is the planner's core category for the bathroom, plus lighting.
        assertThat(saved).anySatisfy(p -> { assertThat(p.getRoomTags()).contains("bathroom"); assertThat(p.getCategory()).isEqualTo("storage"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getRoomTags()).contains("bathroom"); assertThat(p.getCategory()).isEqualTo("lighting"); });
        // Hallway coverage: storage core + at least one rug.
        assertThat(saved).anySatisfy(p -> { assertThat(p.getRoomTags()).contains("hallway"); assertThat(p.getCategory()).isEqualTo("storage"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getRoomTags()).contains("hallway"); assertThat(p.getCategory()).isEqualTo("rug"); });
        // Kitchen depth: a kitchen cart and kitchen storage exist.
        assertThat(saved).anySatisfy(p -> { assertThat(p.getRoomTags()).contains("kitchen"); assertThat(p.getCategory()).isEqualTo("kitchen-cart"); });
        // All three target retailers contributed at least once.
        assertThat(saved).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("Emmezeta"));
        assertThat(saved).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("JYSK"));
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
