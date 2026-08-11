package ai.budgetspace.product;

import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.planner.PlannerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.193 — every shipped row should be REACHABLE, not merely eligible.
 *
 * <p>The 10.193 sweep audited what the planner can actually select and found two categories the catalog
 * carries in volume but no room flow ever asked for: 622 living-room ARMCHAIRS (category {@code chair},
 * which only appeared in the home-office flow) and 160 hallway WARDROBES (the hallway flow asked for
 * {@code storage} but never {@code wardrobe}). Both were invisible in a whole-room plan — a living room
 * could only ever be given a sofa to sit on — and surfaced only if the user named the piece outright,
 * because a focused plan skips the room filter.</p>
 *
 * <p>These tests pin the fix at the level that matters: a real plan, built from the whole shipped catalog,
 * offers those pieces. They are deliberately catalog-driven (no fixtures) so a future re-tagging that
 * strands the rows again fails here.</p>
 */
class Sprint10193RoomCoverageTest {

    @Test
    void aLivingRoomPlanCanOfferAnArmchair() throws Exception {
        List<Product> catalog = importWholeCatalog();
        PlannerService planner = plannerFromCatalog(catalog);

        PlanGenerationResponse response = planner.generateResolved(
                marketRoomInput("Uredi mi dnevni boravak", 2500, "living-room", "HR"));

        assertThat(planCategories(response))
                .as("a living room is no longer limited to a sofa for seating")
                .contains("chair");
    }

    @Test
    void aHallwayPlanCanOfferAWardrobe() throws Exception {
        List<Product> catalog = importWholeCatalog();
        PlannerService planner = plannerFromCatalog(catalog);

        PlanGenerationResponse response = planner.generateResolved(
                marketRoomInput("Uredi mi hodnik", 1200, "hallway", "HR"));

        assertThat(planCategories(response))
                .as("hallway wardrobes are part of a hallway plan")
                .contains("wardrobe");
    }

    @Test
    void theStrandedRowsAreBackedByRealStockInMostMarkets() throws Exception {
        List<Product> eligible = importWholeCatalog().stream()
                .filter(CatalogSourcePolicy::isPlannerEligible)
                .toList();

        long marketsWithLivingRoomChairs = eligible.stream()
                .filter(p -> "chair".equals(p.getCategory()) && roomTags(p).contains("living-room"))
                .map(Product::getMarket).distinct().count();
        long marketsWithHallwayWardrobes = eligible.stream()
                .filter(p -> "wardrobe".equals(p.getCategory()) && roomTags(p).contains("hallway"))
                .map(Product::getMarket).distinct().count();

        // Every market this app serves carries both, so neither addition is a one-market special case.
        assertThat(marketsWithLivingRoomChairs).as("markets with a living-room armchair").isEqualTo(15);
        assertThat(marketsWithHallwayWardrobes).as("markets with a hallway wardrobe").isEqualTo(15);
    }

    // ---- helpers ----

    private static List<String> roomTags(Product p) {
        return p.getRoomTags() == null ? List.of()
                : List.of(p.getRoomTags().split(",")).stream().map(String::trim).collect(Collectors.toList());
    }

    private static List<String> planCategories(PlanGenerationResponse response) {
        return response.plans().stream()
                .flatMap(plan -> plan.items().stream())
                .map(item -> item.product().category())
                .collect(Collectors.toList());
    }

    private PlannerService plannerFromCatalog(List<Product> all) {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(all);
        return new PlannerService(repository);
    }

    private static PlannerInputDto marketRoomInput(String prompt, int budget, String room, String market) {
        return new PlannerInputDto(prompt, budget, room, "modern", "", 8, "multi", List.of(),
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0,
                List.of(), List.of(), market);
    }

    private static List<Product> importWholeCatalog() throws Exception {
        List<Product> saved = new ArrayList<>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenAnswer(inv -> saved.stream()
                .filter(p -> p.getExternalId().equals(inv.getArgument(0))).findFirst());
        when(repository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (!saved.contains(p)) saved.add(p);
            return p;
        });
        RetailerSnapshotImportService importer = new RetailerSnapshotImportService(
                new ProductImportService(repository), new RetailerCatalogAdapter());
        ObjectMapper mapper = new ObjectMapper();
        List<RetailerProductSnapshotDto> all = new ArrayList<>();
        for (String resource : RealCatalogSeeder.snapshotResources()) {
            try (InputStream in = Sprint10193RoomCoverageTest.class.getResourceAsStream(resource)) {
                assertThat(in).as("catalog resource %s", resource).isNotNull();
                all.addAll(mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {}));
            }
        }
        ImportSummaryDto summary = importer.importSnapshot(all);
        assertThat(summary.errors()).as("import errors").isEmpty();
        return saved;
    }
}
