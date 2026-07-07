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
 * Sprint 10.169 — bathroom FIXTURES for Denmark from VVS Eksperten (a DK sanitary-ware specialist), the first
 * non-HR fixtures market. Same guard as Pevex HR: the file imports cleanly, every row is VVS Eksperten / DK /
 * bathroom with a fixture category, and every row is planner-eligible (so it enters a DK bathroom plan) — which
 * only holds because VVS Eksperten is MANUAL_VERIFIED_ONLY, not feed-required.
 */
class VvsEkspertenDkBathroomTest {

    private static final Set<String> FIXTURE_CATEGORIES = Set.of("toilet", "washbasin", "bath-shower");

    @Test
    void vvsEkspertenDkFixturesImportAndArePlannerEligible() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = load("/catalog/real-vvs-eksperten-dk-bathroom-10-169.json");
        assertThat(snapshot).as("VVS Eksperten DK rows").hasSizeGreaterThanOrEqualTo(24);

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
            assertThat(product.getRetailer()).isEqualTo("VVS Eksperten");
            assertThat(product.getMarket()).isEqualTo("DK");
            assertThat(product.getCategory()).as("fixture category %s", product.getExternalId()).isIn(FIXTURE_CATEGORIES);
            assertThat(product.getRoomTags()).as("bathroom tag %s", product.getExternalId()).contains("bathroom");
            assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
            assertThat(product.getProductUrl()).startsWith("https://www.vvs-eksperten.dk/");
            assertThat(product.isImageVerified()).as("imageVerified %s", product.getExternalId()).isTrue();
            assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                    .as("planner-eligible %s", product.getExternalId()).isTrue();
        });

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
