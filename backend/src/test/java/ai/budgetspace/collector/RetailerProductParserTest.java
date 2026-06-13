package ai.budgetspace.collector;

import ai.budgetspace.dto.CollectedProductDto;
import ai.budgetspace.dto.CollectorDefaultsDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetailerProductParserTest {
    private final RetailerProductParser parser = new RetailerProductParser(new ObjectMapper());
    private final CollectorDefaultsDto defaults = new CollectorDefaultsDto("sofa", List.of("living-room"), List.of("modern"), "test-run");

    @Test
    void parsesJsonLdProduct() throws Exception {
        CollectedProductDto product = parser.parse(fixture("generic-product-jsonld.html"), "https://example.com/p/sofa-123", "JYSK", defaults);

        assertThat(product.name()).isEqualTo("Dvosjed svijetli tekstil");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("299.00"));
        assertThat(product.availabilityStatus()).isEqualTo("in-stock");
        assertThat(product.imageUrl()).contains("sofa-123.jpg");
        assertThat(product.externalId()).isEqualTo("SOFA-123");
        assertThat(product.dataQuality()).isEqualTo("partial");
        assertThat(product.sourceType()).isEqualTo("future-scraper");
        assertThat(product.category()).isEqualTo("sofa");
        assertThat(product.roomTags()).containsExactly("living-room");
        assertThat(product.styleTags()).containsExactly("modern");
    }

    @Test
    void fallsBackToOpenGraphWhenNoJsonLd() throws Exception {
        CollectedProductDto product = parser.parse(fixture("generic-product-og.html"), "https://example.com/p/tv-456", "JYSK", defaults);

        assertThat(product.name()).isEqualTo("TV komoda hrast efekt");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("159.00"));
        assertThat(product.availabilityStatus()).isEqualTo("in-stock");
        assertThat(product.dataQuality()).isEqualTo("needs-review");
        assertThat(product.externalId()).startsWith("collected-jysk-");
    }

    @Test
    void missingPriceYieldsNullPrice() throws Exception {
        CollectedProductDto product = parser.parse(fixture("missing-price.html"), "https://example.com/p/rug", "JYSK", defaults);

        assertThat(product.price()).isNull();
        assertThat(product.name()).isEqualTo("Tepih prirodni ton");
    }

    @Test
    void usesDefaultsWhenPageCannotTellCategoryRoomStyle() throws Exception {
        CollectedProductDto product = parser.parse(fixture("generic-product-jsonld.html"), "https://example.com/p/sofa-123", "JYSK", defaults);

        assertThat(product.category()).isEqualTo("sofa");
        assertThat(product.roomTags()).containsExactly("living-room");
        assertThat(product.styleTags()).containsExactly("modern");
    }

    @Test
    void ikeaParserStripsStoreSuffixFromName() {
        String html = "<html><head><title>x</title>"
                + "<meta property=\"og:title\" content=\"Dvosjed svijetli tekstil - IKEA\" />"
                + "<meta property=\"product:price:amount\" content=\"299\" /></head><body></body></html>";

        CollectedProductDto product = parser.parse(html, "https://example.com/p/ikea-sofa", "IKEA", defaults);

        assertThat(product.name()).isEqualTo("Dvosjed svijetli tekstil");
    }

    private String fixture(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/collector/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
