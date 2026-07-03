package ai.budgetspace.dto;

import ai.budgetspace.product.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10.163 (undisclosed-paid-placement guard) — the API boundary must NEVER expose the sponsored/affiliate
 * fields while there is no disclosure UI + ranking guardrail. The Terms promise sponsored links are "clearly
 * labelled"; until that ships, {@link ProductDto#from} neutralizes those fields so no paid flag/link can leak,
 * even if a row is (accidentally or deliberately) flagged sponsored. This pins that guard in place.
 */
class ProductDtoTest {

    @Test
    void sponsoredAndAffiliateFieldsAreNeutralizedAtTheApiBoundary() {
        Product product = new Product();
        product.setId("p1");
        product.setName("Test sofa");
        product.setRetailer("IKEA");
        product.setCategory("sofa");
        product.setPrice(BigDecimal.valueOf(299));
        // A row flagged as a paid placement with a live affiliate redirect + label.
        product.setSponsored(true);
        product.setAffiliateUrl("https://partner.example.com/redirect?to=ikea&pid=p1");
        product.setSponsorLabel("Sponzorirano");

        ProductDto dto = ProductDto.from(product);

        // The client must receive nothing paid: no sponsored flag, no affiliate redirect, no sponsor label.
        assertThat(dto.sponsored()).isFalse();
        assertThat(dto.affiliateUrl()).isNull();
        assertThat(dto.sponsorLabel()).isNull();
    }
}
