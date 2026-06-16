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
 * Sprint 10.15 — proves the production-depth catalog files (IKEA SI/AT/DE depth, JYSK HR/SI/AT/DE,
 * Emmezeta HR depth) all import cleanly through the validated pipeline with zero rejected rows,
 * real product URLs, positive EUR prices, and the {@code public-product-page} provenance. This is the
 * web-verified depth that lets the rule-based planner run rich plans before any LLM spend.
 */
class ProductionDepthCatalogRuntimeTest {

    private static final List<String> DEPTH_RESOURCES = List.of(
            "/catalog/real-ikea-si-depth.json",
            "/catalog/real-ikea-at-depth.json",
            "/catalog/real-ikea-de-depth.json",
            "/catalog/real-jysk-hr-depth.json",
            "/catalog/real-jysk-si.json",
            "/catalog/real-jysk-at.json",
            "/catalog/real-jysk-de.json",
            "/catalog/real-emmezeta-hr-depth.json");

    @Test
    void allDepthCatalogsImportCleanlyWithVerifiedFields() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = loadAll();
        assertThat(snapshot).as("total depth rows").hasSizeGreaterThanOrEqualTo(120);

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
            assertThat(product.getPrice().signum()).as("price > 0 for %s", product.getExternalId()).isPositive();
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(product.getProductUrl()).as("real URL for %s", product.getExternalId()).startsWith("https://");
            assertThat(URI.create(product.getProductUrl()).getHost()).as("host for %s", product.getExternalId()).isNotBlank();
            assertThat(product.getCategory()).isNotBlank();
            assertThat(product.getRoomTags()).as("rooms for %s", product.getExternalId()).isNotBlank();
            assertThat(product.getStyleTags()).as("styles for %s", product.getExternalId()).isNotBlank();
            assertThat(ProductTaxonomy.canEnterPlanner(product)).isTrue();
        });

        // Bedroom + dining coverage now exists in the EU markets (the gap this sprint filled).
        assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo("DE"); assertThat(p.getCategory()).isEqualTo("bed"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo("AT"); assertThat(p.getCategory()).isEqualTo("dining-table"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo("SI"); assertThat(p.getCategory()).isEqualTo("wardrobe"); });
        // JYSK now exists outside HR.
        assertThat(saved).anySatisfy(p -> { assertThat(p.getRetailer()).isEqualTo("JYSK"); assertThat(p.getMarket()).isEqualTo("DE"); });
    }

    static List<RetailerProductSnapshotDto> loadAll() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<RetailerProductSnapshotDto> all = new ArrayList<>();
        for (String resource : DEPTH_RESOURCES) {
            try (InputStream in = ProductionDepthCatalogRuntimeTest.class.getResourceAsStream(resource)) {
                assertThat(in).as("catalog resource %s", resource).isNotNull();
                all.addAll(mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {}));
            }
        }
        return all;
    }
}
