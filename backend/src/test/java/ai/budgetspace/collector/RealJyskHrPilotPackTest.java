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
 * Validates the curated real JYSK HR living-room pilot pack (Sprint 10.2). Reads the JSON
 * from {@code docs/pilot-packs} — it never calls the live internet. If the file cannot be
 * located from the test working directory the test is skipped rather than failed.
 */
class RealJyskHrPilotPackTest {

    @Test
    void realJyskHrLivingRoomPackIsValid() throws Exception {
        Path pack = locatePack();
        assumeTrue(pack != null, "Pilot pack nije pronađen iz radnog direktorija testa — preskačem.");

        JsonNode root = new ObjectMapper().readTree(pack.toFile());
        assertThat(root.path("retailer").asText()).isEqualTo("JYSK");

        JsonNode items = root.path("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isBetween(10, 14);

        for (JsonNode item : items) {
            String url = item.path("url").asText();
            assertThat(url).startsWith("https://");
            String host = URI.create(url).getHost();
            assertThat(host).isIn("jysk.hr", "www.jysk.hr");

            JsonNode defaults = item.path("defaults");
            assertThat(defaults.isObject()).isTrue();
            // category must be normalizable
            assertThat(ProductTaxonomy.normalizeCategory(defaults.path("category").asText())).isPresent();
            // roomTags must include at least one entry and include living-room
            assertThat(defaults.path("roomTags").isArray()).isTrue();
            assertThat(defaults.path("roomTags").size()).isGreaterThan(0);
            boolean containsLivingRoom = false;
            for (JsonNode tag : defaults.path("roomTags")) {
                if ("living-room".equals(tag.asText())) {
                    containsLivingRoom = true;
                    break;
                }
            }
            assertThat(containsLivingRoom).isTrue();
            // styleTags must be non-empty and normalizable
            assertThat(defaults.path("styleTags").isArray()).isTrue();
            assertThat(defaults.path("styleTags").size()).isGreaterThan(0);
            for (JsonNode style : defaults.path("styleTags")) {
                assertThat(ProductTaxonomy.normalizeStyle(style.asText())).isPresent();
            }
            assertThat(defaults.path("sourceReference").asText()).isEqualTo("jysk-hr-living-room-real-pilot-10-2");
        }
    }

    private Path locatePack() {
        String[] candidates = {
                "docs/pilot-packs/real-jysk-hr-living-room-pilot.json",
                "../docs/pilot-packs/real-jysk-hr-living-room-pilot.json"
        };
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.exists(path)) return path;
        }
        return null;
    }
}