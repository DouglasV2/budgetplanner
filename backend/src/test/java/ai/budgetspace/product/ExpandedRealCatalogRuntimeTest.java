package ai.budgetspace.product;

import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.5 — proves the expanded real IKEA HR + JYSK HR living-room catalog (both resources
 * that {@link RealCatalogSeeder} loads at startup) imports through the runtime pipeline with real,
 * priced, living-room products and real product URLs. No live internet: read from the classpath.
 *
 * <p>The minimum counts here are the <em>honestly web-verified</em> totals. The sprint target was
 * ~200 products (IKEA ~100 / JYSK ~100); only products whose name, price and live product URL could
 * be verified are included (no fabricated data), so the asserted floor is lower than the target.</p>
 */
class ExpandedRealCatalogRuntimeTest {

    static final List<String> RESOURCES = List.of(
            "/catalog/real-ikea-jysk-hr-living-room.json",
            "/catalog/real-ikea-jysk-hr-living-room-expansion.json");

    @Test
    void expandedCatalogImportsRealLivingRoomProductsWithRealUrls() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = loadAll();

        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(Optional.empty());
        List<Product> saved = new ArrayList<>();
        when(repository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            saved.add(product);
            return product;
        });

        ImportSummaryDto summary = new RetailerSnapshotImportService(
                new ProductImportService(repository), new RetailerCatalogAdapter()).importSnapshot(snapshot);

        // Sprint 10.167: the live dead-link sweep removed 16 verified-dead HR living-room rows (discontinued
        // IKEA/JYSK products that 404 or bounce to a category), so these floors dropped from 76/41/35 to the
        // post-removal reality (60/29/31). They stay a regression floor; a sourcing backfill only raises them.
        assertThat(summary.created()).isGreaterThanOrEqualTo(58);
        long ikea = saved.stream().filter(product -> "IKEA".equals(product.getRetailer())).count();
        long jysk = saved.stream().filter(product -> "JYSK".equals(product.getRetailer())).count();
        assertThat(ikea).as("verified IKEA products").isGreaterThanOrEqualTo(28);
        assertThat(jysk).as("verified JYSK products").isGreaterThanOrEqualTo(30);

        for (Product product : saved) {
            assertThat(product.getName()).as("name").isNotBlank();
            assertThat(product.getRetailer()).isIn("IKEA", "JYSK");
            assertThat(product.getCategory()).as("category").isNotBlank();
            assertThat(product.getPrice()).as("price").isNotNull();
            assertThat(product.getPrice().signum()).as("price > 0 for %s", product.getExternalId()).isPositive();
            assertThat(hasLivingRoom(product.getRoomTags()))
                    .as("living-room tag for %s", product.getExternalId()).isTrue();
            assertThat(isRealProductUrl(product.getProductUrl()))
                    .as("real product URL (not homepage/placeholder) for %s: %s", product.getExternalId(), product.getProductUrl())
                    .isTrue();
        }
    }

    static boolean hasLivingRoom(String roomTags) {
        return roomTags != null && Arrays.stream(roomTags.split(","))
                .anyMatch(tag -> tag.trim().equalsIgnoreCase("living-room"));
    }

    static boolean isRealProductUrl(String url) {
        if (url == null || url.isBlank() || url.startsWith("#")) return false;
        URI uri;
        try {
            uri = URI.create(url);
        } catch (RuntimeException invalid) {
            return false;
        }
        String host = uri.getHost();
        String path = uri.getPath();
        boolean okHost = host != null
                && (host.endsWith("ikea.com") || host.endsWith("ikea.hr") || host.endsWith("jysk.hr"));
        boolean realPath = path != null && path.length() > 1 && !path.equals("/hr/hr/");
        return okHost && realPath;
    }

    static List<RetailerProductSnapshotDto> loadAll() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<RetailerProductSnapshotDto> all = new ArrayList<>();
        for (String resource : RESOURCES) {
            try (InputStream in = ExpandedRealCatalogRuntimeTest.class.getResourceAsStream(resource)) {
                assertThat(in).as("catalog resource %s", resource).isNotNull();
                all.addAll(mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {}));
            }
        }
        return all;
    }
}
