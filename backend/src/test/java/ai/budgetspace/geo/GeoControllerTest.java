package ai.budgetspace.geo;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GeoControllerTest {

    private final GeoController controller = new GeoController();

    @Test
    void readsTheCloudflareCountryHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-IPCountry", "FR");

        GeoDto geo = controller.geo(request);

        assertThat(geo.country()).isEqualTo("FR");
        assertThat(geo.source()).isEqualTo("CF-IPCountry");
    }

    @Test
    void normalisesTheCountryToUppercase() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CloudFront-Viewer-Country", "nl");

        assertThat(controller.geo(request).country()).isEqualTo("NL");
    }

    @Test
    void returnsNullWhenNoGeoHeaderIsPresent() {
        GeoDto geo = controller.geo(new MockHttpServletRequest());

        assertThat(geo.country()).isNull();
        assertThat(geo.source()).isNull();
    }

    @Test
    void ignoresAGarbageCountryValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Country", "France"); // not a 2-letter ISO code

        assertThat(controller.geo(request).country()).isNull();
    }

    @Test
    void prefersTheHigherPriorityHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Country", "DE");
        request.addHeader("CF-IPCountry", "ES"); // CloudFlare has priority over the generic header

        assertThat(controller.geo(request).country()).isEqualTo("ES");
    }
}
