package ai.budgetspace.product;

import ai.budgetspace.dto.CatalogHealthDto;
import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 16 — catalog-health count. Verifies the shipped catalog (every snapshot in
 * {@link RealCatalogSeeder#snapshotResources()}, imported exactly as the boot seeder does) has the expected size and
 * that {@code /api/products/catalog-health} ({@link CatalogHealthService#compute()}) reports it.
 *
 * <p>Deterministic (classpath only, no DB/HTTP/live probing). NOTE: in PRODUCTION the rolling freshness re-check +
 * dead-link retirement (Sprint 10.166/10.167) can mark some rows unavailable over time, so a live deployment's
 * {@code totalProducts} may be ≤ this shipped count — that is by design, not a discrepancy in the data.</p>
 */
class CatalogHealthCountTest {

    // Sprint 10.184: +7471 cross-market IKEA products (real-ikea-catalog-15k-10-184.json) took the shipped catalog
    // from 11_233 past the 15k target to 18_704 (distinct externalIds across every snapshot the seeder imports).
    private static final int EXPECTED_TOTAL = 18_704;

    @Test
    void shippedCatalogHasTheExpectedProductCount() throws Exception {
        List<Product> catalog = importWholeCatalog();
        assertThat(catalog).as("shipped catalog size (distinct externalIds across all snapshots)").hasSize(EXPECTED_TOTAL);
    }

    @Test
    void catalogHealthEndpointReportsTheShippedTotal() throws Exception {
        List<Product> catalog = importWholeCatalog();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(catalog);

        CatalogHealthDto health = new CatalogHealthService(repository).compute();

        assertThat(health.totalProducts()).as("/catalog-health totalProducts").isEqualTo(EXPECTED_TOTAL);
        assertThat(health.usableProducts()).as("most of the catalog is planner-usable").isGreaterThan(EXPECTED_TOTAL / 2);
        assertThat(health.usableProducts() + health.blockedProducts())
                .as("every product is either usable or blocked").isEqualTo(EXPECTED_TOTAL);
        assertThat(health.byRetailer().getOrDefault("IKEA", 0)).as("IKEA present").isPositive();
    }

    private static List<Product> importWholeCatalog() throws Exception {
        // externalId -> product, mirroring the real repository's unique-index lookup so importing the whole
        // (now 18k-row) catalog stays O(n), not the O(n^2) a linear list scan would cost at this size.
        Map<String, Product> byExternalId = new HashMap<>();
        List<Product> saved = new ArrayList<>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(byExternalId.get((String) inv.getArgument(0))));
        when(repository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (byExternalId.put(p.getExternalId(), p) == null) saved.add(p);
            return p;
        });
        RetailerSnapshotImportService importer = new RetailerSnapshotImportService(
                new ProductImportService(repository), new RetailerCatalogAdapter());
        ObjectMapper mapper = new ObjectMapper();
        List<RetailerProductSnapshotDto> all = new ArrayList<>();
        for (String resource : RealCatalogSeeder.snapshotResources()) {
            try (InputStream in = CatalogHealthCountTest.class.getResourceAsStream(resource)) {
                assertThat(in).as("catalog resource %s", resource).isNotNull();
                all.addAll(mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {}));
            }
        }
        ImportSummaryDto summary = importer.importSnapshot(all);
        assertThat(summary.errors()).as("import errors").isEmpty();
        return saved;
    }
}
