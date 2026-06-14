package ai.budgetspace.collector;

import ai.budgetspace.product.ProductTaxonomy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Validates the curated real IKEA HR living-room pilot pack (Sprint 10.1). Reads the JSON
 * from {@code docs/pilot-packs} — it never calls the live internet. If the file cannot be
 * located from the test working directory the test is skipped rather than failed.
 */
class RealIkeaHrPilotPackTest {

    @Test
    void realIkeaHrLivingRoomPackIsValid() throws Exception {
        Path pack = locatePack();
        assumeTrue(pack != null, "Pilot pack nije pronađen iz radnog direktorija testa — preskačem.");

        JsonNode root = new ObjectMapper().readTree(pack.toFile());
        assertThat(root.path("retailer").asText()).isEqualTo("IKEA");

        JsonNode items = root.path("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isBetween(10, 14);

        for (JsonNode item : items) {
            String url = item.path("url").asText();
            assertThat(url).startsWith("https://");
            assertThat(URI.create(url).getHost()).isIn("ikea.com", "www.ikea.com");

            JsonNode defaults = item.path("defaults");
            assertThat(defaults.isObject()).isTrue();
            assertThat(ProductTaxonomy.normalizeCategory(defaults.path("category").asText())).isPresent();
            assertThat(defaults.path("roomTags").isArray()).isTrue();
            assertThat(defaults.path("roomTags").size()).isGreaterThan(0);
            assertThat(defaults.path("styleTags").isArray()).isTrue();
            assertThat(defaults.path("styleTags").size()).isGreaterThan(0);
            assertThat(defaults.path("sourceReference").asText()).isNotBlank();
        }
    }

    private Path locatePack() {
        String[] candidates = {
                "docs/pilot-packs/real-ikea-hr-living-room-pilot.json",
                "../docs/pilot-packs/real-ikea-hr-living-room-pilot.json"
        };
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.exists(path)) return path;
        }
        return null;
    }
}
