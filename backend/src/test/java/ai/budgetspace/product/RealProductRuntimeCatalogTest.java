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
 * Sprint 10.4 — proves the real IKEA HR + JYSK HR living-room catalog (the one
 * {@code RealCatalogSeeder} loads at startup) imports through the runtime import pipeline with
 * real product URLs. No live internet: the snapshot is read from the classpath resource.
 */
class RealProductRuntimeCatalogTest {

    @Test
    void realSnapshotImportsIkeaAndJyskProductsWithRealProductUrls() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = loadSnapshot();
        assertThat(snapshot).isNotEmpty();

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

        assertThat(summary.created()).isGreaterThanOrEqualTo(40);
        long ikea = saved.stream().filter(product -> "IKEA".equals(product.getRetailer())).count();
        long jysk = saved.stream().filter(product -> "JYSK".equals(product.getRetailer())).count();
        assertThat(ikea).isGreaterThanOrEqualTo(20);
        assertThat(jysk).isGreaterThanOrEqualTo(20);

        for (Product product : saved) {
            assertThat(product.getRetailer()).isIn("IKEA", "JYSK");
            assertThat(product.getProductUrl()).isNotBlank();
            assertThat(isRealProductUrl(product.getProductUrl()))
                    .as("real product URL, not a homepage: %s", product.getProductUrl())
                    .isTrue();
        }
    }

    @Test
    void stripLivingRoomTagRemovesLivingRoomKeepsOtherRooms() {
        assertThat(RealCatalogSeeder.stripLivingRoomTag("living-room")).isBlank();
        assertThat(RealCatalogSeeder.stripLivingRoomTag("living-room,bedroom")).isEqualTo("bedroom");
        assertThat(RealCatalogSeeder.stripLivingRoomTag("bedroom,living-room,home-office")).isEqualTo("bedroom,home-office");
        assertThat(RealCatalogSeeder.stripLivingRoomTag("bedroom")).isEqualTo("bedroom");
    }

    private boolean isRealProductUrl(String url) {
        if (url == null) return false;
        URI uri = URI.create(url);
        String host = uri.getHost();
        String path = uri.getPath();
        boolean okHost = "www.ikea.com".equals(host) || "ikea.com".equals(host) || "jysk.hr".equals(host) || "www.jysk.hr".equals(host);
        boolean realPath = path != null && path.length() > 1 && !path.equals("/hr/hr/");
        return okHost && realPath;
    }

    private List<RetailerProductSnapshotDto> loadSnapshot() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/catalog/real-ikea-jysk-hr-living-room.json")) {
            assertThat(in).as("real catalog snapshot resource").isNotNull();
            return new ObjectMapper().readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }
}
