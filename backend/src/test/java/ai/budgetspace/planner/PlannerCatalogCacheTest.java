package ai.budgetspace.planner;

import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductImportService;
import ai.budgetspace.product.ProductRepository;
import ai.budgetspace.product.RetailerCatalogAdapter;
import ai.budgetspace.product.RetailerSnapshotImportService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.39 (perf) — guards the catalog-snapshot cache. {@code marketCatalog()} is called many times
 * while a single plan is built; before the cache each call hit {@code productRepository.findAll()} (a full
 * table scan that grows with the catalog). The short-TTL snapshot collapses them into one load per request.
 * This test proves a single {@code generate()} triggers at most a couple of {@code findAll()} calls (not the
 * ~dozen the un-cached path made) while still producing a real plan.
 */
class PlannerCatalogCacheTest {

    @Test
    void aSinglePlanGenerationLoadsTheCatalogOnce() throws Exception {
        List<Product> catalog = importedCatalog();
        ProductRepository repo = mock(ProductRepository.class);
        when(repo.findAll()).thenReturn(catalog);
        PlannerService planner = new PlannerService(repo);

        var plan = planner.generate(new PlannerInputDto(
                "Woonkamer tot 1500 €, modern, IKEA en JYSK.", 1500, "living-room",
                "modern", "Amsterdam", 24, "multi", List.of("IKEA", "JYSK"), "best-value", "comfort",
                List.of(), List.of(), List.of(), List.of(), List.of(), 0).withMarket("NL"));

        // The plan is real (the cache must not change behaviour)...
        assertThat(plan.plans()).isNotEmpty();
        assertThat(plan.plans().get(0).items()).isNotEmpty();
        // ...and the whole request hit the database at most twice (cached), not once per category/retailer pass.
        verify(repo, atMost(2)).findAll();
    }

    private List<Product> importedCatalog() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = new ArrayList<>();
        snapshot.addAll(load("/catalog/real-ikea-nl-rooms.json"));
        snapshot.addAll(load("/catalog/real-jysk-nl-rooms.json"));
        List<Product> saved = new ArrayList<>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenAnswer(inv -> saved.stream()
                .filter(p -> p.getExternalId().equals(inv.getArgument(0))).findFirst());
        when(repository.save(any(Product.class))).thenAnswer(inv -> { saved.add(inv.getArgument(0)); return inv.getArgument(0); });
        new RetailerSnapshotImportService(new ProductImportService(repository), new RetailerCatalogAdapter())
                .importSnapshot(snapshot);
        return saved;
    }

    private List<RetailerProductSnapshotDto> load(String resource) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertThat(in).as("catalog resource %s", resource).isNotNull();
            return mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }
}
