package ai.budgetspace.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the private helpers on {@link RetailerProductParser} that deal with
 * price parsing and availability mapping. Reflection is used because these helpers are
 * intentionally package-private/private, and we want to verify their behaviour without
 * exposing them as public API.
 */
class RetailerProductParserPriceTest {

    @Test
    void parsesPricesWithDecimalCommaAndThousandSeparators() throws Exception {
        RetailerProductParser parser = new RetailerProductParser(new ObjectMapper());
        Method parsePrice = RetailerProductParser.class.getDeclaredMethod("parsePrice", String.class);
        parsePrice.setAccessible(true);

        assertThat((BigDecimal) parsePrice.invoke(parser, "1.299,00 €")).isEqualByComparingTo(new BigDecimal("1299.00"));
        assertThat((BigDecimal) parsePrice.invoke(parser, "899,99 kn")).isEqualByComparingTo(new BigDecimal("899.99"));
        assertThat((BigDecimal) parsePrice.invoke(parser, "1 099.50")).isEqualByComparingTo(new BigDecimal("1099.50"));
        assertThat((BigDecimal) parsePrice.invoke(parser, "2,999")).isEqualByComparingTo(new BigDecimal("2.999"));
    }

    @Test
    void mapsVariousAvailabilityStrings() throws Exception {
        RetailerProductParser parser = new RetailerProductParser(new ObjectMapper());
        Method mapAvailability = RetailerProductParser.class.getDeclaredMethod("mapAvailability", String.class);
        mapAvailability.setAccessible(true);

        assertThat((String) mapAvailability.invoke(parser, "In stock")).isEqualTo("in-stock");
        assertThat((String) mapAvailability.invoke(parser, "Available")).isEqualTo("in-stock");
        assertThat((String) mapAvailability.invoke(parser, "Limited availability")).isEqualTo("limited");
        assertThat((String) mapAvailability.invoke(parser, "Out of stock")).isEqualTo("unavailable");
        assertThat((String) mapAvailability.invoke(parser, "Unavailable")).isEqualTo("unavailable");
        assertThat((String) mapAvailability.invoke(parser, "Preorder now")).isEqualTo("check-store");
        assertThat((String) mapAvailability.invoke(parser, "Unknown")).isEqualTo("check-store");
    }
}