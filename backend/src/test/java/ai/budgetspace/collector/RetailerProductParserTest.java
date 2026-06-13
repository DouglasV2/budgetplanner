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
        CollectedProductDto product = parse("generic-product-jsonld.html", "https://example.com/p/sofa-123", "JYSK");

        assertThat(product.name()).isEqualTo("Dvosjed svijetli tekstil");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("299.00"));
        assertThat(product.availabilityStatus()).isEqualTo("in-stock");
        assertThat(product.imageUrl()).contains("sofa-123.jpg");
        assertThat(product.externalId()).isEqualTo("SOFA-123");
        assertThat(product.dataQuality()).isEqualTo("complete");
        assertThat(product.sourceType()).isEqualTo("future-scraper");
        assertThat(product.category()).isEqualTo("sofa");
        assertThat(product.roomTags()).containsExactly("living-room");
        assertThat(product.styleTags()).containsExactly("modern");
    }

    @Test
    void fallsBackToOpenGraphWithWarning() throws Exception {
        RetailerProductParser.ParsedProduct result = parser.parse(
                fixture("generic-product-og.html"), "https://example.com/p/tv-456", "JYSK", defaults);

        assertThat(result.product().name()).isEqualTo("TV komoda hrast efekt");
        assertThat(result.product().price()).isEqualByComparingTo(new BigDecimal("159.00"));
        assertThat(result.product().availabilityStatus()).isEqualTo("in-stock");
        assertThat(result.product().dataQuality()).isEqualTo("partial");
        assertThat(result.product().externalId()).startsWith("collected-jysk-");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("fallback"));
    }

    @Test
    void missingPriceYieldsNeedsReview() throws Exception {
        CollectedProductDto product = parse("missing-price.html", "https://example.com/p/rug", "JYSK");

        assertThat(product.price()).isNull();
        assertThat(product.name()).isEqualTo("Tepih prirodni ton");
        assertThat(product.dataQuality()).isEqualTo("needs-review");
    }

    @Test
    void usesDefaultsWhenPageCannotTellCategoryRoomStyle() throws Exception {
        CollectedProductDto product = parse("generic-product-jsonld.html", "https://example.com/p/sofa-123", "JYSK");

        assertThat(product.category()).isEqualTo("sofa");
        assertThat(product.roomTags()).containsExactly("living-room");
        assertThat(product.styleTags()).containsExactly("modern");
    }

    @Test
    void parserDoesNotCrashOnEmptyHtml() {
        RetailerProductParser.ParsedProduct result = parser.parse("", "https://example.com/p/x", "IKEA", defaults);

        assertThat(result.product().price()).isNull();
        assertThat(result.product().name()).isNull();
    }

    // --- IKEA parser v1 --------------------------------------------------------

    @Test
    void parsesIkeaJsonLdCompleteAndCleansNameAndArticleId() throws Exception {
        CollectedProductDto product = parse("ikea-product-jsonld-complete.html", "https://www.ikea.com/hr/hr/p/dvosjed-bjork-30299118/", "IKEA");

        assertThat(product.name()).isEqualTo("Dvosjed BJORK svijetli");
        assertThat(product.externalId()).isEqualTo("ikea-30299118");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("299.00"));
        assertThat(product.availabilityStatus()).isEqualTo("in-stock");
        assertThat(product.dataQuality()).isEqualTo("complete");
    }

    @Test
    void ikeaMissingPriceIsNeedsReview() throws Exception {
        CollectedProductDto product = parse("ikea-product-jsonld-missing-price.html", "https://www.ikea.com/hr/hr/p/stolic-lack-20011408/", "IKEA");

        assertThat(product.price()).isNull();
        assertThat(product.name()).isEqualTo("Stolić LACK bijeli");
        assertThat(product.dataQuality()).isEqualTo("needs-review");
    }

    @Test
    void ikeaOpenGraphFallbackCleansNameAndWarns() throws Exception {
        RetailerProductParser.ParsedProduct result = parser.parse(
                fixture("ikea-product-og-fallback.html"), "https://www.ikea.com/hr/hr/p/tv-komoda-besta-40123456/", "IKEA", defaults);

        assertThat(result.product().name()).isEqualTo("TV komoda BESTA hrast");
        assertThat(result.product().price()).isEqualByComparingTo(new BigDecimal("149.00"));
        assertThat(result.product().externalId()).isEqualTo("ikea-40123456");
        assertThat(result.product().dataQuality()).isEqualTo("partial");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("fallback"));
    }

    @Test
    void ikeaUnavailableMapsToUnavailable() throws Exception {
        CollectedProductDto product = parse("ikea-product-unavailable.html", "https://www.ikea.com/hr/hr/p/fotelja-strandmon-10342678/", "IKEA");

        assertThat(product.availabilityStatus()).isEqualTo("unavailable");
        assertThat(product.name()).isEqualTo("Fotelja STRANDMON siva");
    }

    @Test
    void ikeaExternalIdIsStableForSameUrl() throws Exception {
        String url = "https://www.ikea.com/hr/hr/p/dvosjed-bjork-30299118/";
        String first = parse("ikea-product-jsonld-complete.html", url, "IKEA").externalId();
        String second = parse("ikea-product-jsonld-complete.html", url, "IKEA").externalId();

        assertThat(first).isEqualTo(second).isEqualTo("ikea-30299118");
    }

    private CollectedProductDto parse(String fixtureName, String url, String retailer) throws Exception {
        return parser.parse(fixture(fixtureName), url, retailer, defaults).product();
    }

    private String fixture(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/collector/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
