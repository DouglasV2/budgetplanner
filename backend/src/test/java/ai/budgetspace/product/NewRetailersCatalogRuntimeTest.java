package ai.budgetspace.product;

import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.16 — HR kitchen depth + additional verified retailers (Harvey Norman HR/SI, Namjestaj.hr,
 * Otto/Segmüller/Poco DE). Proves they import cleanly (every retailer is supported, every row valid)
 * and that the sourcing policy classifies the new retailers correctly (fetchable+verified vs the
 * named-but-blocked ones that stay feed-required).
 */
class NewRetailersCatalogRuntimeTest {

    private static final List<String> RESOURCES = List.of(
            "/catalog/real-hr-kitchen.json",
            "/catalog/real-harvey-norman.json",
            "/catalog/real-namjestaj-hr.json",
            "/catalog/real-de-new-retailers.json");

    @Test
    void newRetailerCatalogsImportCleanly() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = loadAll();
        assertThat(snapshot).hasSizeGreaterThanOrEqualTo(45);

        List<Product> saved = new ArrayList<>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            saved.add(product);
            return product;
        });

        ImportSummaryDto summary = new RetailerSnapshotImportService(
                new ProductImportService(repository), new RetailerCatalogAdapter()).importSnapshot(snapshot);

        assertThat(summary.errors()).as("rejected rows: %s", summary.errors()).isEmpty();
        assertThat(summary.created()).isEqualTo(snapshot.size());
        assertThat(saved).allSatisfy(p -> {
            assertThat(ProductTaxonomy.normalizeRetailer(p.getRetailer())).as("supported retailer %s", p.getRetailer()).isPresent();
            assertThat(p.getPrice().signum()).isPositive();
            assertThat(p.getProductUrl()).startsWith("https://");
            assertThat(ProductTaxonomy.canEnterPlanner(p)).isTrue();
        });

        // The new fetchable retailers actually produced products.
        assertThat(saved).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("Harvey Norman"));
        assertThat(saved).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("Namjestaj.hr"));
        assertThat(saved).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("Otto"));
        // HR kitchen coverage now exists.
        assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo("HR"); assertThat(p.getCategory()).isIn("kitchen-cart", "kitchen-storage"); });
    }

    @Test
    void sourcingPolicyClassifiesNewRetailers() {
        // Reachable + hand-verified (have products):
        for (String r : new String[] {"Harvey Norman", "Namjestaj.hr", "Otto", "Segmüller", "Poco"}) {
            assertThat(CatalogSourcePolicy.statusFor(r))
                    .as("%s", r).isEqualTo(CatalogSourcePolicy.SourcingStatus.MANUAL_VERIFIED_ONLY);
            assertThat(CatalogSourcePolicy.isFeedRequired(r)).isFalse();
        }
        // Named but blocked / unusable for direct import → feed-required (never scraped):
        for (String r : new String[] {"Momax", "Bauhaus", "FeroTerm", "Wayfair", "Home24", "Roller", "Kika", "Leiner", "XXXLutz", "Merkur", "Dipo", "Prima Namještaj", "Perfecta Dreams"}) {
            assertThat(CatalogSourcePolicy.isFeedRequired(r)).as("%s feed-required", r).isTrue();
        }
        // All are registered/supported so the system is aware of them.
        for (String r : new String[] {"Harvey Norman", "Otto", "Momax", "Bauhaus", "Kika", "Wayfair"}) {
            assertThat(ProductTaxonomy.normalizeRetailer(r)).as("supported %s", r).isPresent();
        }
    }

    private List<RetailerProductSnapshotDto> loadAll() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<RetailerProductSnapshotDto> all = new ArrayList<>();
        for (String resource : RESOURCES) {
            try (InputStream in = getClass().getResourceAsStream(resource)) {
                assertThat(in).as("catalog resource %s", resource).isNotNull();
                all.addAll(mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {}));
            }
        }
        return all;
    }
}
