package ai.budgetspace.product;

import ai.budgetspace.dto.ProductDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

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
}
