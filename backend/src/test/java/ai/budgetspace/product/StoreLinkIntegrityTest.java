package ai.budgetspace.product;

import ai.budgetspace.dto.ProductDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10.5 — "Otvori u trgovini" integrity. Every product in the real catalog must carry a real
 * store link (never a homepage, "#", placeholder or blank), and that link must survive into the
 * {@link ProductDto} the frontend renders.
 */
class StoreLinkIntegrityTest {

    @Test
    void everyCatalogProductHasARealStoreLink() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = ExpandedRealCatalogRuntimeTest.loadAll();
        assertThat(snapshot).isNotEmpty();

        for (RetailerProductSnapshotDto product : snapshot) {
            String url = product.productUrl();
            assertThat(url).as("productUrl for %s", product.externalId()).isNotBlank();
            assertThat(url).as("no placeholder link for %s", product.externalId()).doesNotStartWith("#");
            assertThat(url).isNotEqualTo("https://www.ikea.com/hr/hr/");
            assertThat(url).isNotEqualTo("https://jysk.hr/");
            assertThat(url).isNotEqualTo("https://www.jysk.hr/");
            assertThat(ExpandedRealCatalogRuntimeTest.isRealProductUrl(url))
                    .as("real product link (ikea.com/ikea.hr/jysk.hr product page) for %s: %s", product.externalId(), url)
                    .isTrue();
        }
    }

    @Test
    void productUrlReachesTheDtoTheFrontendUses() {
        Product product = new Product();
        product.setId("link-test");
        product.setExternalId("link-test");
        product.setName("Test sofa");
        product.setRetailer("IKEA");
        product.setCategory("sofa");
        product.setPrice(BigDecimal.valueOf(199));
        product.setRoomTags("living-room");
        product.setStyleTags("modern");
        product.setProductUrl("https://www.ikea.com/hr/hr/p/test-12345678/");
        product.setUrl("https://www.ikea.com/hr/hr/p/test-12345678/");

        ProductDto dto = ProductDto.from(product);

        assertThat(dto.productUrl()).isEqualTo("https://www.ikea.com/hr/hr/p/test-12345678/");
        assertThat(dto.retailer()).isEqualTo("IKEA");
        assertThat(dto.price()).isEqualByComparingTo(BigDecimal.valueOf(199));
    }

    /**
     * Build-time guard (TASKS #10b / road-to-production step 3): no two catalog rows may point at the
     * same retailer product URL. Two rows sharing a URL under different {@code externalId}s import as
     * redundant catalog entries (the import only dedupes by {@code externalId}). This loads every
     * resource the {@link RealCatalogSeeder} actually imports, so a future catalog file is covered
     * automatically. If this fails, collapse the duplicate to a single row (keep one {@code externalId},
     * union the {@code roomTags}).
     */
    @Test
    void noTwoCatalogProductsShareAProductUrl() throws Exception {
        Map<String, List<String>> byUrl = new LinkedHashMap<>();
        for (RetailerProductSnapshotDto product : loadEntireCatalog()) {
            byUrl.computeIfAbsent(normalizeUrl(product.productUrl()), key -> new ArrayList<>())
                    .add(product.externalId());
        }

        Map<String, List<String>> duplicates = new LinkedHashMap<>();
        byUrl.forEach((url, ids) -> {
            if (ids.size() > 1) duplicates.put(url, ids);
        });

        assertThat(duplicates)
                .as("productUrls shared by >1 catalog row (externalIds per url): %s", duplicates)
                .isEmpty();
    }

    /** The import pipeline dedupes by {@code externalId}; a duplicate would silently overwrite a row. */
    @Test
    void noTwoCatalogProductsShareAnExternalId() throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (RetailerProductSnapshotDto product : loadEntireCatalog()) {
            counts.merge(product.externalId(), 1L, Long::sum);
        }

        Map<String, Long> duplicates = new LinkedHashMap<>();
        counts.forEach((id, count) -> {
            if (count > 1) duplicates.put(id, count);
        });

        assertThat(duplicates).as("externalIds used by >1 catalog row: %s", duplicates).isEmpty();
    }

    /** Normalises a product URL for comparison: trims, lower-cases, drops query/hash and trailing slashes. */
    private static String normalizeUrl(String url) {
        if (url == null) return "";
        String trimmed = url.trim().toLowerCase();
        int cut = trimmed.indexOf('?');
        if (cut >= 0) trimmed = trimmed.substring(0, cut);
        cut = trimmed.indexOf('#');
        if (cut >= 0) trimmed = trimmed.substring(0, cut);
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    /** Every catalog row the {@link RealCatalogSeeder} imports at startup (the authoritative list). */
    private static List<RetailerProductSnapshotDto> loadEntireCatalog() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<RetailerProductSnapshotDto> all = new ArrayList<>();
        for (String resource : RealCatalogSeeder.snapshotResources()) {
            try (InputStream in = StoreLinkIntegrityTest.class.getResourceAsStream(resource)) {
                assertThat(in).as("catalog resource %s", resource).isNotNull();
                all.addAll(mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {}));
            }
        }
        return all;
    }
}
