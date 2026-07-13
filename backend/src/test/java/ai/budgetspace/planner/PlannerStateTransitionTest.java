package ai.budgetspace.planner;

import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.MoveInRequestDto;
import ai.budgetspace.dto.MoveInResponse;
import ai.budgetspace.dto.MoveInRoomDto;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlanItemDto;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductImportService;
import ai.budgetspace.product.ProductRepository;
import ai.budgetspace.product.RealCatalogSeeder;
import ai.budgetspace.product.RetailerCatalogAdapter;
import ai.budgetspace.product.RetailerSnapshotImportService;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 11 — state-transition behaviour.
 *
 * <p>The frontend re-sends a fresh request per generate, writing the last plan's resolved room/budget/categories/
 * retailers back into the form (Planner.tsx applyResponse), carrying only {@code roomInferred}. These tests drive the
 * SAME service path the frontend uses ({@code generate} = generate-fast, {@code generateMoveIn}) across realistic
 * request sequences and assert that each new request reflects ONLY its own parameters — an earlier request's room,
 * budget, owned/excluded categories or retailer choice must never bleed into the next one. It also locks the
 * inferred-vs-explicit room rule (Bug 2026-07-10) and confirms the degraded-capacity signal never false-alarms on a
 * stale cross-room must-have.</p>
 */
class PlannerStateTransitionTest {

    private static PlannerService planner;

    @BeforeAll
    static void importCatalogOnce() throws Exception {
        planner = plannerFromCatalog(importWholeCatalog());
    }

    // ---------- room transitions: a new prompt overrides a carried, merely-inferred room ----------

    @Test
    void bathroomToBedroom_newPromptWins_noFixtureLeak_noFalsePartial() {
        PlanGenerationResponse first = planner.generate(req("Uredi mi kupaonicu, treba mi umivaonik", "living-room", true, 2500));
        assertThat(first.input().roomType()).isEqualTo("bathroom");
        assertThat(first.input().mustHaveCategories()).contains("washbasin");

        // Exact frontend write-back: carry the resolved room + must-haves, flip in the NEW prompt.
        PlanGenerationResponse second = planner.generate(writeBack(first, "Sada mi složi spavaću sobu, treba mi krevet"));

        assertThat(second.input().roomType()).as("the new prompt's room wins over the carried inferred one").isEqualTo("bedroom");
        assertThat(categories(second)).contains("bed").doesNotContain("washbasin", "toilet");
        // The carried bathroom must-have must NOT be reported as a bedroom market-limitation.
        assertThat(second.partialPlan()).as("a stale cross-room must-have must not falsely flag the plan").isFalse();
    }

    @Test
    void livingRoomToKitchen_newPromptWins_noSofaLeak() {
        PlanGenerationResponse first = planner.generate(req("Dnevni boravak, treba mi kauč i TV komoda", "living-room", true, 2500));
        assertThat(first.input().roomType()).isEqualTo("living-room");

        PlanGenerationResponse second = planner.generate(writeBack(first, "Zapravo mi treba kuhinja s kuhinjskim kolicima"));
        assertThat(second.input().roomType()).isEqualTo("kitchen");
        assertThat(categories(second)).doesNotContain("sofa", "tv-unit");
        assertThat(second.plans().get(0).items()).isNotEmpty();
    }

    @Test
    void kitchenToBathroom_newPromptWins_noKitchenLeak() {
        PlanGenerationResponse first = planner.generate(req("Kuhinja, treba mi frižider", "living-room", true, 2500));
        assertThat(first.input().roomType()).isEqualTo("kitchen");

        PlanGenerationResponse second = planner.generate(writeBack(first, "Sada kupaonica, treba mi umivaonik i ormarić"));
        assertThat(second.input().roomType()).isEqualTo("bathroom");
        assertThat(categories(second)).contains("washbasin").doesNotContain("fridge", "kitchen-cart");
    }

    @Test
    void bathroomToStudio_newPromptWins_noWashbasinLeak() {
        PlanGenerationResponse first = planner.generate(req("Kupaonica, treba mi tuš", "living-room", true, 3000));
        assertThat(first.input().roomType()).isEqualTo("bathroom");

        PlanGenerationResponse second = planner.generate(writeBack(first, "Zapravo je to garsonijera, treba mi krevet i kauč"));
        assertThat(second.input().roomType()).isEqualTo("studio");
        assertThat(categories(second)).contains("bed").doesNotContain("washbasin", "shower", "toilet");
    }

    // ---------- inferred vs explicitly-chosen room ----------

    @Test
    void explicitlyChosenRoom_isHonoredOverThePrompt() {
        // roomInferred=false → the user deliberately picked Bathroom in the form; a living-room-ish prompt must NOT flip it.
        PlanGenerationResponse r = planner.generate(req("treba mi kauč", "bathroom", false, 2500));
        assertThat(r.input().roomType()).as("an explicit room pick is honored").isEqualTo("bathroom");
    }

    @Test
    void inferredRoom_isOverridableByThePrompt() {
        PlanGenerationResponse r = planner.generate(req("spavaća soba, treba mi krevet", "bathroom", true, 2500));
        assertThat(r.input().roomType()).as("an inferred room is re-derived from the prompt").isEqualTo("bedroom");
    }

    // ---------- budget change does not carry over ----------

    @Test
    void budgetChangeIsReflected_andDoesNotBleedBetweenRequests() {
        PlanGenerationResponse high = planner.generate(structured("living-room", 3500, List.of(), List.of(), List.of(), "HR"));
        PlanGenerationResponse low = planner.generate(structured("living-room", 800, List.of(), List.of(), List.of(), "HR"));
        double highTotal = total(high);
        double lowTotal = total(low);
        assertThat(highTotal).as("a bigger budget buys a bigger plan").isGreaterThan(lowTotal);
        assertThat(lowTotal).as("the low request respects its own budget, not the earlier high one").isLessThanOrEqualTo(800 * 1.2);

        // Re-run the low request AFTER the high one: identical total → no budget bled across requests.
        double lowAgain = total(planner.generate(structured("living-room", 800, List.of(), List.of(), List.of(), "HR")));
        assertThat(lowAgain).isEqualTo(lowTotal);
    }

    // ---------- already-owned removal brings the category back ----------

    @Test
    void removingAnAlreadyOwnedCategoryBringsItBack() {
        PlanGenerationResponse owns = planner.generate(structured("living-room", 2500, List.of(), List.of("sofa"), List.of(), "HR"));
        PlanGenerationResponse fresh = planner.generate(structured("living-room", 2500, List.of(), List.of(), List.of(), "HR"));
        assertThat(categories(owns)).as("owning a sofa keeps it out of the plan").doesNotContain("sofa");
        assertThat(categories(fresh)).as("removing 'already own sofa' brings the sofa back").contains("sofa");
    }

    // ---------- excluded (avoid) category change does not persist ----------

    @Test
    void changingTheExcludedCategory_dropsOnlyTheCurrentOne() {
        PlanGenerationResponse noRug = planner.generate(structured("living-room", 3000, List.of(), List.of("rug"), List.of(), "HR"));
        PlanGenerationResponse noLighting = planner.generate(structured("living-room", 3000, List.of(), List.of("lighting"), List.of(), "HR"));
        assertThat(categories(noRug)).doesNotContain("rug");
        assertThat(categories(noLighting)).as("the earlier rug exclusion must be gone").contains("rug").doesNotContain("lighting");
    }

    // ---------- retailer preference change / removal ----------

    @Test
    void retailerExclusionIsPerRequest_andRemovable() {
        PlanGenerationResponse noIkea = planner.generate(structured("living-room", 3000, List.of(), List.of(), List.of("IKEA"), "HR"));
        assertThat(retailers(noIkea)).as("IKEA excluded").doesNotContain("IKEA");

        // Switch the exclusion to JYSK: the earlier IKEA exclusion must not persist.
        PlanGenerationResponse noJysk = planner.generate(structured("living-room", 3000, List.of(), List.of(), List.of("JYSK"), "HR"));
        assertThat(retailers(noJysk)).doesNotContain("JYSK");
        assertThat(retailers(noJysk)).as("the prior IKEA exclusion did not carry over").contains("IKEA");

        // Remove all exclusions: IKEA is available again.
        PlanGenerationResponse cleared = planner.generate(structured("living-room", 3000, List.of(), List.of(), List.of(), "HR"));
        assertThat(retailers(cleared)).contains("IKEA");
    }

    // ---------- single-room ↔ Move-In ----------

    @Test
    void singleRoomThenMoveIn_doesNotLeakFixturesAcrossRooms() {
        // A single bathroom request first (with a fixture must-have)…
        PlanGenerationResponse single = planner.generate(structured("bathroom", 2500, List.of("toilet"), List.of(), List.of(), "HR"));
        assertThat(categories(single)).contains("toilet"); // HR does stock toilets

        // …then the user switches to whole-apartment mode. Move-In builds each room independently.
        PlannerInputDto base = structured("living-room", 0, List.of(), List.of(), List.of(), "HR");
        MoveInResponse moveIn = planner.generateMoveIn(new MoveInRequestDto(base, List.of("bedroom", "bathroom"), 6000));

        assertThat(moveIn.rooms()).hasSize(2);
        assertThat(moveIn.grandTotal().signum()).isPositive();
        MoveInRoomDto bedroom = moveIn.rooms().stream().filter(r -> r.roomType().equals("bedroom")).findFirst().orElseThrow();
        MoveInRoomDto bathroom = moveIn.rooms().stream().filter(r -> r.roomType().equals("bathroom")).findFirst().orElseThrow();
        assertThat(roomCategories(bedroom)).as("the earlier toilet must-have must not force a toilet into the bedroom")
                .doesNotContain("toilet", "washbasin").contains("bed");
        assertThat(roomCategories(bathroom)).as("the bathroom room still furnishes normally").isNotEmpty();
    }

    @Test
    void moveInThenSingleRoom_singleRoomIsUnaffected() {
        PlannerInputDto base = structured("living-room", 0, List.of(), List.of(), List.of(), "HR");
        planner.generateMoveIn(new MoveInRequestDto(base, List.of("living-room", "kitchen"), 5000));

        // A fresh single-room request afterwards is a normal, complete kitchen plan (no Move-In residue).
        PlanGenerationResponse kitchen = planner.generate(structured("kitchen", 1800, List.of(), List.of(), List.of(), "HR"));
        assertThat(kitchen.plans()).hasSize(3);
        assertThat(kitchen.plans().get(0).items()).isNotEmpty();
    }

    // ---------- the service keeps no residue between requests ----------

    @Test
    void serviceIsStateless_anInterveningRequestLeavesNoResidue() {
        PlanGenerationResponse bathroomFresh = planner.generate(structured("bathroom", 2500, List.of("washbasin"), List.of(), List.of(), "HR"));
        planner.generate(structured("living-room", 4000, List.of("sofa", "tv-unit"), List.of(), List.of(), "HR")); // unrelated
        PlanGenerationResponse bathroomAgain = planner.generate(structured("bathroom", 2500, List.of("washbasin"), List.of(), List.of(), "HR"));

        assertThat(categories(bathroomAgain)).isEqualTo(categories(bathroomFresh));
        assertThat(total(bathroomAgain)).isEqualTo(total(bathroomFresh));
        assertThat(bathroomAgain.partialPlan()).isEqualTo(bathroomFresh.partialPlan());
    }

    // ---------- helpers ----------

    private static PlannerInputDto req(String prompt, String roomType, boolean roomInferred, int budget) {
        return new PlannerInputDto(prompt, budget, roomType, "modern", "", 20, "multi", List.of(),
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0,
                List.of(), List.of(), "HR").withRoomInferred(roomInferred);
    }

    private static PlannerInputDto structured(String roomType, int budget, List<String> mustHave,
                                              List<String> alreadyHave, List<String> excludedRetailers, String market) {
        return new PlannerInputDto("", budget, roomType, "modern", "", 20, "multi", List.of(),
                "best-value", "comfort", mustHave, alreadyHave, List.of(), List.of(), excludedRetailers, 0,
                List.of(), List.of(), market).withRoomInferred(false);
    }

    // Mirror Planner.tsx applyResponse: carry the resolved room + must-haves + retailers, keep roomInferred, new prompt.
    private static PlannerInputDto writeBack(PlanGenerationResponse previous, String newPrompt) {
        PlannerInputDto p = previous.input();
        return new PlannerInputDto(newPrompt, p.budget(), p.roomType(), p.style(), p.location(), p.size(), p.retailerMode(),
                p.selectedRetailers(), p.optimizationGoal(), p.furnishingLevel(), p.mustHaveCategories(),
                p.alreadyHaveCategories(), List.of(), p.preferredRetailers(), p.excludedRetailers(), p.maxStores(),
                p.colorPreferences(), p.materialPreferences(), p.market()).withRoomInferred(true);
    }

    private static List<String> categories(PlanGenerationResponse r) {
        return r.plans().get(0).items().stream().map(i -> i.product().category()).toList();
    }

    private static List<String> roomCategories(MoveInRoomDto room) {
        return room.plans().isEmpty() ? List.of()
                : room.plans().get(0).items().stream().map(i -> i.product().category()).toList();
    }

    private static Set<String> retailers(PlanGenerationResponse r) {
        return r.plans().get(0).items().stream().map(i -> i.product().retailer()).collect(Collectors.toSet());
    }

    private static double total(PlanGenerationResponse r) {
        return r.plans().get(0).total().doubleValue();
    }

    private static PlannerService plannerFromCatalog(List<Product> all) {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(all);
        return new PlannerService(repository);
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
            try (InputStream in = PlannerStateTransitionTest.class.getResourceAsStream(resource)) {
                assertThat(in).as("catalog resource %s", resource).isNotNull();
                all.addAll(mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {}));
            }
        }
        ImportSummaryDto summary = importer.importSnapshot(all);
        assertThat(summary.errors()).as("import errors").isEmpty();
        return saved;
    }
}
