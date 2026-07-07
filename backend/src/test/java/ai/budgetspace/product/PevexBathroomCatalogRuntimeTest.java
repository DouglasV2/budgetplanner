package ai.budgetspace.product;

import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.169 — bathroom FIXTURES (sanitary ware) from Pevex HR, the pieces IKEA/JYSK don't sell. Proves the
 * new file imports cleanly, every row is Pevex / HR / bathroom with a new fixture category
 * (toilet / washbasin / bath-shower), and — crucially — every row is planner-eligible (so it actually enters a
 * bathroom plan). This guards the CatalogSourcePolicy flip: if Pevex ever reverts to OFFICIAL_FEED_REQUIRED, its
 * {@code public-product-page} rows would silently stop being planner-eligible and this test would catch it.
 */
class PevexBathroomCatalogRuntimeTest {

    private static final Set<String> FIXTURE_CATEGORIES = Set.of("toilet", "washbasin", "bath-shower");

    @Test
    void pevexBathroomFixturesImportAndArePlannerEligible() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = load("/catalog/real-pevex-hr-bathroom-10-169.json");
        assertThat(snapshot).as("Pevex bathroom rows").hasSizeGreaterThanOrEqualTo(20);

        List<Product> saved = new ArrayList<>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenAnswer(inv -> saved.stream()
                .filter(p -> p.getExternalId().equals(inv.getArgument(0))).findFirst());
        when(repository.save(any(Product.class))).thenAnswer(inv -> { saved.add(inv.getArgument(0)); return inv.getArgument(0); });

        ImportSummaryDto summary = new RetailerSnapshotImportService(
                new ProductImportService(repository), new RetailerCatalogAdapter()).importSnapshot(snapshot);
        assertThat(summary.errors()).as("rejected rows: %s", summary.errors()).isEmpty();
        assertThat(summary.created()).isEqualTo(snapshot.size());

        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getRetailer()).isEqualTo("Pevex");
            assertThat(product.getMarket()).isEqualTo("HR");
            assertThat(product.getCategory()).as("fixture category %s", product.getExternalId()).isIn(FIXTURE_CATEGORIES);
            assertThat(product.getRoomTags()).as("bathroom tag %s", product.getExternalId()).contains("bathroom");
            assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
            assertThat(product.getProductUrl()).startsWith("https://www.pevex.hr/");
            assertThat(product.isImageVerified()).as("imageVerified %s", product.getExternalId()).isTrue();
            // The whole point: Pevex is MANUAL_VERIFIED_ONLY (not feed-required), so a public-product-page row IS
            // planner-eligible and can enter a bathroom plan.
            assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                    .as("planner-eligible %s", product.getExternalId()).isTrue();
        });

        // The catalog must cover each fixture type so a bathroom plan can offer a toilet, a washbasin AND a bath/shower.
        for (String category : FIXTURE_CATEGORIES) {
            assertThat(saved).as("covers %s", category).anySatisfy(p -> assertThat(p.getCategory()).isEqualTo(category));
        }
    }

    private List<RetailerProductSnapshotDto> load(String resource) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertThat(in).as("catalog resource %s", resource).isNotNull();
            return mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }
}
