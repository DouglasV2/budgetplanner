package ai.budgetspace.planner;

import ai.budgetspace.dto.AdjustRoomDto;
import ai.budgetspace.dto.CompleteKitchenDto;
import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.dto.MoveInAdjustRequest;
import ai.budgetspace.dto.MoveInRequestDto;
import ai.budgetspace.dto.MoveInResponse;
import ai.budgetspace.dto.MoveInRoomDto;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlanItemDto;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.ProductDto;
import ai.budgetspace.dto.ReplaceProductRequest;
import ai.budgetspace.dto.SimilarItemsRequest;
import ai.budgetspace.dto.SimilarItemsResponse;
import ai.budgetspace.dto.StoreTotalDto;
import ai.budgetspace.dto.StoreTripDto;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlannerServiceTest {
    @Test
    void budgetSentenceDoesNotRemoveSofaButAlreadyHaveDoes() {
        PlannerService service = serviceWithProducts(defaultProducts());

        FurnishingPlanDto budgetPlan = service.generate(input("Imam 1500 € za dnevni boravak, želim moderno."))
                .plans()
                .get(0);
        assertThat(categories(budgetPlan)).contains("sofa");

        FurnishingPlanDto alreadyHavePlan = service.generate(input("Imam 1500 € za dnevni boravak i već imam kauč."))
                .plans()
                .get(0);
        assertThat(categories(alreadyHavePlan)).doesNotContain("sofa");
    }

    // Sprint 10.190: "jeftino" must not mean "worst thing on the shelf". Two sofas close in price but far apart
    // in quality — a price-led plan has to take the better-rated one.
    @Test
    void aCheapPlanStillPrefersTheBetterRatedPieceInItsBand() {
        List<Product> products = new ArrayList<>(defaultProducts());
        products.add(product("sofa-floor", "Jeftini slabi", "JYSK", "sofa", 200, 2.8));
        products.add(product("sofa-good", "Jeftini dobar", "IKEA", "sofa", 250, 4.9));
        PlannerService service = serviceWithProducts(products);

        FurnishingPlanDto plan = service.generate(input("dnevni boravak, sto jeftinije").withBudget(1500)).plans().get(0);
        assertThat(plan.items()).extracting(item -> item.product().id())
                .as("the better-rated sofa wins even on the cheapest goal")
                .contains("sofa-good")
                .doesNotContain("sofa-floor");
    }

    // Sprint 10.190: a softened style ("ne previše moderno") should land on a piece that reads as BOTH modern and
    // classic, rather than the purely modern one.
    @Test
    void aSoftenedStylePrefersAPieceThatReadsAsBoth() {
        Product modernOnly = product("sofa-modern-only", "Samo moderan", "IKEA", "sofa", 400, 4.5);
        modernOnly.setStyleTags("modern");
        // Same retailer for both, so the retailer-diversity tie-break can't be what decides this.
        Product both = product("sofa-both", "Moderan i klasičan", "IKEA", "sofa", 400, 4.5);
        both.setStyleTags("modern,classic");
        PlannerService service = serviceWithProducts(List.of(modernOnly, both));

        PlannerInputDto blended = input("dnevni boravak").withStyle("modern").withSecondaryStyles(List.of("classic"));
        FurnishingPlanDto plan = service.generate(blended).plans().get(0);

        assertThat(plan.items()).extracting(item -> item.product().id())
                .as("the piece carrying both styles wins the slot")
                .contains("sofa-both")
                .doesNotContain("sofa-modern-only");
    }

    @Test
    void planKeepsOneProductPerCategoryWhenNothingIsLocked() {
        PlannerService service = serviceWithProducts(defaultProducts());
        PlannerInputDto input = input("Imam 1500 € za dnevni boravak.");
        ProductDto sofaA = ProductDto.from(product("sofa-a", "Sofa A", "IKEA", "sofa", 600, 4.5));
        ProductDto sofaB = ProductDto.from(product("sofa-b", "Sofa B", "JYSK", "sofa", 500, 4.4));
        ProductDto table = ProductDto.from(product("table-1", "Stolić", "IKEA", "table", 90, 4.2));
        FurnishingPlanDto dirty = new FurnishingPlanDto(
                "value",
                "Najbolji izbor",
                "Najbolji omjer",
                "Opis",
                "",
                "",
                "",
                "",
                "",
                "",
                List.of(),
                List.of(),
                List.of(
                        new PlanItemDto(sofaA, "Prva opcija", "buy-first", "Ovo je glavni komad", "1. Najvažnije za početak"),
                        new PlanItemDto(sofaB, "Duplikat", "buy-first", "Ovo je glavni komad", "1. Najvažnije za početak"),
                        new PlanItemDto(table, "Za maknuti", "buy-first", "Ovo je glavni komad", "1. Najvažnije za početak")
                ),
                BigDecimal.valueOf(1190),
                BigDecimal.valueOf(310),
                80,
                "Low",
                80,
                List.of("IKEA", "JYSK"),
                null,
                null,
                null,
                null,
                null
        );

        FurnishingPlanDto cleaned = service.replaceProduct(new ReplaceProductRequest(dirty, input, "table-1", "remove"));

        assertThat(categories(cleaned)).containsExactly("sofa");
        assertThat(cleaned.total()).isEqualByComparingTo(BigDecimal.valueOf(600));
    }

    @Test
    void cheaperReplacementIsActuallyCheaper() {
        Product expensiveSofa = product("sofa-expensive", "Skuplji kauč", "IKEA", "sofa", 900, 4.8);
        Product cheaperSofa = product("sofa-cheap", "Povoljniji kauč", "IKEA", "sofa", 520, 4.2);
        PlannerService service = serviceWithProducts(List.of(expensiveSofa, cheaperSofa));
        PlannerInputDto input = input("Imam 1500 € za dnevni boravak.");
        FurnishingPlanDto plan = new FurnishingPlanDto(
                "value",
                "Najbolji izbor",
                "Najbolji omjer",
                "Opis",
                "",
                "",
                "",
                "",
                "",
                "",
                List.of(),
                List.of(),
                List.of(new PlanItemDto(ProductDto.from(expensiveSofa), "Glavni komad", "buy-first", "Ovo je glavni komad", "1. Najvažnije za početak")),
                BigDecimal.valueOf(900),
                BigDecimal.valueOf(600),
                80,
                "Low",
                80,
                List.of("IKEA"),
                null,
                null,
                null,
                null,
                null
        );

        FurnishingPlanDto replaced = service.replaceProduct(new ReplaceProductRequest(plan, input, "sofa-expensive", "cheaper"));

        assertThat(replaced.items()).hasSize(1);
        assertThat(replaced.items().get(0).product().id()).isEqualTo("sofa-cheap");
        assertThat(replaced.items().get(0).product().price()).isLessThan(BigDecimal.valueOf(900));
    }

    // Sprint 10.183: the strict "nicer" band collapses toward ~1.25x the current price once the budget is mostly
    // spent, so for a sparse category (a rug) a genuinely nicer, pricier piece just above that ceiling was never
    // found and the swap silently did nothing (the owner's "Nađi ljepše za tepih nije ga htio zamijeniti"). The
    // widened fallback must return that real step up.
    @Test
    void nicerReplacementWidensBeyondTheTightBandForSparseCategories() {
        Product sofa = product("sofa-1", "Kauč", "IKEA", "sofa", 1400, 4.5);
        Product rugAnchor = product("rug-anchor", "Tepih obični", "JYSK", "rug", 120, 4.0);
        Product rugNicer = product("rug-nicer", "Tepih ljepši", "JYSK", "rug", 220, 4.7);
        PlannerService service = serviceWithProducts(List.of(sofa, rugAnchor, rugNicer));
        PlannerInputDto input = input("Imam 1500 € za dnevni boravak.");
        // sofa 1400 leaves only ~100 headroom, so the strict nicer ceiling collapses below the 220€ rug.
        FurnishingPlanDto plan = planWith(sofa, rugAnchor);

        FurnishingPlanDto replaced = service.replaceProduct(new ReplaceProductRequest(plan, input, "rug-anchor", "nicer"));

        assertThat(replaced.items()).extracting(item -> item.product().id())
                .contains("rug-nicer")
                .doesNotContain("rug-anchor");
    }

    // A "nicer" swap must stay honest: when the current piece is already the nicest/priciest one available, the
    // plan comes back UNCHANGED (so the UI can tell the user there's no nicer option) — it must never swap DOWN to
    // a cheaper, worse-rated piece just to return "something".
    @Test
    void nicerReplacementStaysHonestWhenNothingIsGenuinelyNicer() {
        Product sofa = product("sofa-1", "Kauč", "IKEA", "sofa", 1300, 4.5);
        Product rugAnchor = product("rug-anchor", "Tepih vrhunski", "JYSK", "rug", 200, 4.8);
        Product rugWorse = product("rug-worse", "Tepih slabiji", "JYSK", "rug", 80, 4.0);
        PlannerService service = serviceWithProducts(List.of(sofa, rugAnchor, rugWorse));
        PlannerInputDto input = input("Imam 1500 € za dnevni boravak.");
        FurnishingPlanDto plan = planWith(sofa, rugAnchor);

        FurnishingPlanDto replaced = service.replaceProduct(new ReplaceProductRequest(plan, input, "rug-anchor", "nicer"));

        assertThat(replaced.items()).extracting(item -> item.product().id())
                .contains("rug-anchor")
                .doesNotContain("rug-worse");
    }

    // A candidate can sit INSIDE the price band yet not be a genuine step up (a touch cheaper AND lower rated).
    // "Find nicer" must not swap to it — that would replace the piece with a plainly worse one and call it nicer.
    @Test
    void nicerReplacementDoesNotSwapToASlightlyCheaperLowerRatedPiece() {
        Product sofa = product("sofa-1", "Kauč", "IKEA", "sofa", 1300, 4.5);
        Product rugAnchor = product("rug-anchor", "Tepih vrhunski", "JYSK", "rug", 200, 4.8);
        Product rugSidegrade = product("rug-side", "Tepih sličan", "JYSK", "rug", 190, 4.2); // 0.95x, lower rated
        PlannerService service = serviceWithProducts(List.of(sofa, rugAnchor, rugSidegrade));
        PlannerInputDto input = input("Imam 1500 € za dnevni boravak.");
        FurnishingPlanDto plan = planWith(sofa, rugAnchor);

        FurnishingPlanDto replaced = service.replaceProduct(new ReplaceProductRequest(plan, input, "rug-anchor", "nicer"));

        assertThat(replaced.items()).extracting(item -> item.product().id())
                .contains("rug-anchor")
                .doesNotContain("rug-side");
    }

    // Sprint 10.173 (P0 — similar-item + budget discovery).
    @Test
    void findSimilarReturnsThreeDistinctBucketsWithinCap() {
        Product cheap = product("sofa-cheap", "Povoljni kauč", "IKEA", "sofa", 120, 3.5);
        Product mid = product("sofa-mid", "Uravnoteženi kauč", "IKEA", "sofa", 300, 4.9);
        Product nice = product("sofa-nice", "Ljepši kauč", "JYSK", "sofa", 500, 4.7);
        Product anchor = product("sofa-anchor", "Trenutni kauč", "IKEA", "sofa", 400, 4.0);
        PlannerService service = serviceWithProducts(List.of(cheap, mid, nice, anchor));

        SimilarItemsResponse res = service.findSimilar(
                new SimilarItemsRequest(ProductDto.from(anchor), input("Imam 1500 € za dnevni boravak."), 600));

        // The cheapest is the budget pick, the balanced (higher-rated mid) is best value, the priciest steps up.
        assertThat(res.budgetPick().id()).isEqualTo("sofa-cheap");
        assertThat(res.bestValue().id()).isEqualTo("sofa-mid");
        assertThat(res.nicer().id()).isEqualTo("sofa-nice");
        // All three distinct, all within the cap, and the nicer step is strictly above the best-value pick.
        assertThat(res.budgetPick().price()).isLessThan(res.bestValue().price());
        assertThat(res.nicer().price()).isGreaterThan(res.bestValue().price());
        assertThat(res.nicer().price()).isLessThanOrEqualTo(BigDecimal.valueOf(600));
        assertThat(res.cap()).isEqualTo(600);
        assertThat(res.currency()).isEqualTo("EUR");
        // The anchor is never suggested back to the user.
        assertThat(List.of(res.budgetPick().id(), res.bestValue().id(), res.nicer().id()))
                .doesNotContain("sofa-anchor");
    }

    @Test
    void findSimilarExcludesTheAnchorAndAnythingOverTheCap() {
        Product cheap = product("sofa-cheap", "Povoljni kauč", "IKEA", "sofa", 120, 4.0);
        Product mid = product("sofa-mid", "Srednji kauč", "IKEA", "sofa", 300, 4.6);
        Product over = product("sofa-over", "Preskupi kauč", "JYSK", "sofa", 500, 4.8);
        Product anchor = product("sofa-anchor", "Trenutni kauč", "IKEA", "sofa", 280, 4.2);
        PlannerService service = serviceWithProducts(List.of(cheap, mid, over, anchor));

        SimilarItemsResponse res = service.findSimilar(
                new SimilarItemsRequest(ProductDto.from(anchor), input("Imam 1500 € za dnevni boravak."), 350));

        // Only the two under-cap products can be suggested; the over-cap one and the anchor never appear.
        assertThat(res.budgetPick()).isNotNull();
        assertThat(res.bestValue()).isNotNull();
        assertThat(res.nicer()).isNull();
        List<String> ids = new ArrayList<>();
        ids.add(res.budgetPick().id());
        ids.add(res.bestValue().id());
        assertThat(ids).doesNotContain("sofa-over", "sofa-anchor");
        assertThat(res.budgetPick().price()).isLessThanOrEqualTo(BigDecimal.valueOf(350));
        assertThat(res.bestValue().price()).isLessThanOrEqualTo(BigDecimal.valueOf(350));
    }

    @Test
    void findSimilarReturnsEmptyBucketsWhenNothingFitsInsteadOfFabricating() {
        Product a = product("sofa-a", "Kauč A", "IKEA", "sofa", 120, 4.5);
        Product b = product("sofa-b", "Kauč B", "JYSK", "sofa", 300, 4.6);
        Product anchor = product("sofa-anchor", "Trenutni kauč", "IKEA", "sofa", 400, 4.0);
        PlannerService service = serviceWithProducts(List.of(a, b, anchor));

        SimilarItemsResponse res = service.findSimilar(
                new SimilarItemsRequest(ProductDto.from(anchor), input("Imam 1500 € za dnevni boravak."), 50));

        assertThat(res.budgetPick()).isNull();
        assertThat(res.bestValue()).isNull();
        assertThat(res.nicer()).isNull();
        assertThat(res.cap()).isEqualTo(50);
    }

    @Test
    void findSimilarStaysInTheRequestedMarket() {
        Product hrA = product("sofa-hr-a", "HR kauč A", "IKEA", "sofa", 150, 4.3);
        hrA.setMarket("HR");
        Product hrB = product("sofa-hr-b", "HR kauč B", "IKEA", "sofa", 320, 4.7);
        hrB.setMarket("HR");
        Product si = product("sofa-si", "SI kauč", "IKEA", "sofa", 200, 4.9);
        si.setMarket("SI");
        Product anchor = product("sofa-anchor", "Trenutni kauč", "IKEA", "sofa", 400, 4.0);
        anchor.setMarket("HR");
        PlannerService service = serviceWithProducts(List.of(hrA, hrB, si, anchor));

        SimilarItemsResponse res = service.findSimilar(
                new SimilarItemsRequest(ProductDto.from(anchor), input("Imam 1500 € za dnevni boravak."), 600));

        List<String> ids = new ArrayList<>();
        if (res.budgetPick() != null) ids.add(res.budgetPick().id());
        if (res.bestValue() != null) ids.add(res.bestValue().id());
        if (res.nicer() != null) ids.add(res.nicer().id());
        assertThat(ids).doesNotContain("sofa-si");
        assertThat(ids).contains("sofa-hr-a", "sofa-hr-b");
    }

    @Test
    void findSimilarNeverSuggestsSecondHandListings() {
        Product retail = product("sofa-new", "Novi kauč", "IKEA", "sofa", 300, 4.6);
        Product used = product("sofa-used", "Rabljeni kauč", "IKEA", "sofa", 150, 4.9);
        used.setSecondHand(true);
        Product anchor = product("sofa-anchor", "Trenutni kauč", "IKEA", "sofa", 400, 4.0);
        PlannerService service = serviceWithProducts(List.of(retail, used, anchor));

        SimilarItemsResponse res = service.findSimilar(
                new SimilarItemsRequest(ProductDto.from(anchor), input("Imam 1500 € za dnevni boravak."), 600));

        // Only the new-retail product can be suggested; the used listing is never in a bucket.
        assertThat(res.bestValue().id()).isEqualTo("sofa-new");
        assertThat(res.budgetPick()).isNull();
        assertThat(res.nicer()).isNull();
    }

    // Sprint 10.175 (kitchen Increment 1 — complete-kitchen modular sets).
    @Test
    void completeKitchenReturnsSetsWithinBudgetRankedAndMarketScoped() {
        Product setCheap = product("set-1", "KNOXHULT kuhinja 220cm", "IKEA", "kitchen-set", 900, 4.4);
        Product setMid = product("set-2", "ENHET kuhinja 243cm", "IKEA", "kitchen-set", 1600, 4.7);
        Product setOver = product("set-3", "Velika kuhinja 340cm", "IKEA", "kitchen-set", 5200, 4.8);
        Product sofa = product("sofa-1", "Kauč", "IKEA", "sofa", 600, 4.5); // must NOT appear
        for (Product s : List.of(setCheap, setMid, setOver, sofa)) s.setRoomTags("kitchen");
        PlannerService service = serviceWithProducts(List.of(setCheap, setMid, setOver, sofa));

        var brief = new KitchenIntentClassifier.KitchenBrief(
                KitchenIntentClassifier.KitchenIntent.COMPLETE, KitchenIntentClassifier.KitchenShape.L_SHAPED, true);
        CompleteKitchenDto ck = service.buildCompleteKitchen(input("kompletna kuhinja").withBudget(2000), brief);

        List<String> ids = ck.sets().stream().map(ProductDto::id).toList();
        assertThat(ids).contains("set-1", "set-2");           // both under 2000
        assertThat(ids).doesNotContain("set-3", "sofa-1");    // over budget / not a set
        assertThat(ck.showModularNote()).isTrue();
        assertThat(ck.shape()).isEqualTo("l-shaped");
        assertThat(ck.includeAppliances()).isTrue();
    }

    @Test
    void completeKitchenIsHonestlyEmptyWhenNothingFits() {
        Product setOver = product("set-1", "Skupa kuhinja", "IKEA", "kitchen-set", 5000, 4.8);
        setOver.setRoomTags("kitchen");
        PlannerService service = serviceWithProducts(List.of(setOver));
        var brief = new KitchenIntentClassifier.KitchenBrief(
                KitchenIntentClassifier.KitchenIntent.COMPLETE, KitchenIntentClassifier.KitchenShape.UNKNOWN, false);
        CompleteKitchenDto ck = service.buildCompleteKitchen(input("kompletna kuhinja").withBudget(2000), brief);
        assertThat(ck.sets()).isEmpty();
        assertThat(ck.showModularNote()).isTrue(); // the honest modular note still shows
    }

    @Test
    void generateAttachesCompleteKitchenOnCompletePrompt() {
        Product set = product("set-1", "KNOXHULT kuhinja", "IKEA", "kitchen-set", 900, 4.5);
        set.setRoomTags("kitchen");
        PlannerService service = serviceWithProducts(List.of(set));
        PlanGenerationResponse res = service.generate(input("trebam kompletnu kuhinju do 2000 eura").withBudget(2000));
        assertThat(res.completeKitchen()).isNotNull();
        assertThat(res.completeKitchen().sets()).extracting(ProductDto::id).contains("set-1");
    }

    @Test
    void generateDoesNotAttachCompleteKitchenOnAPlainRoomPrompt() {
        PlannerService service = serviceWithProducts(defaultProducts());
        PlanGenerationResponse res = service.generate(input("dnevni boravak s kaučem"));
        assertThat(res.completeKitchen()).isNull(); // NONE intent → no section, normal plan unaffected
    }

    // Sprint 10.176 (kitchen Increment 3): a named appliance is picked into the normal plan.
    @Test
    void mustHaveApplianceIsPickedIntoTheKitchenPlan() {
        Product fridge = product("fridge-1", "Hladnjak KYLIG", "IKEA", "fridge", 349, 4.5);
        fridge.setRoomTags("kitchen");
        Product cart = product("cart-1", "RÅSKOG kolica", "IKEA", "kitchen-cart", 40, 4.4);
        cart.setRoomTags("kitchen");
        PlannerService service = serviceWithProducts(List.of(fridge, cart));
        // generate() runs the extractor: "trebam frižider za kuhinju" → must-have fridge + kitchen room.
        PlanGenerationResponse res = service.generate(input("trebam frižider za kuhinju do 800 eura").withBudget(800));
        List<String> categories = res.plans().stream()
                .flatMap(plan -> plan.items().stream())
                .map(item -> item.product().category())
                .toList();
        assertThat(categories).contains("fridge");
        // A generic kitchen prompt (no appliance named) must NOT force the fridge into the plan.
        PlanGenerationResponse generic = service.generate(input("opremi kuhinju do 800 eura").withBudget(800));
        List<String> genericCats = generic.plans().stream()
                .flatMap(plan -> plan.items().stream())
                .map(item -> item.product().category())
                .toList();
        assertThat(genericCats).doesNotContain("fridge");
    }

    @Test
    void maybeAttachCompleteKitchenUsesTheOriginalPromptWhenResolvedPromptIsCleared() {
        Product set = product("set-1", "KNOXHULT kuhinja", "IKEA", "kitchen-set", 900, 4.5);
        set.setRoomTags("kitchen");
        PlannerService service = serviceWithProducts(List.of(set));
        // Simulate the AI path: the resolved input's prompt is cleared, and no section is attached yet.
        PlannerInputDto cleared = input("").withBudget(2000);
        PlanGenerationResponse base = new PlanGenerationResponse(cleared, List.of(), false, List.of(), null);

        PlanGenerationResponse attached = service.maybeAttachCompleteKitchen(base, "trebam kompletnu kuhinju do 2000 eura", cleared);
        assertThat(attached.completeKitchen()).isNotNull();
        assertThat(attached.completeKitchen().sets()).extracting(ProductDto::id).contains("set-1");

        // A non-complete original prompt leaves it null, and an already-attached section is left untouched.
        assertThat(service.maybeAttachCompleteKitchen(base, "dnevni boravak s kaučem", cleared).completeKitchen()).isNull();
        assertThat(service.maybeAttachCompleteKitchen(attached, "dnevni boravak", cleared)).isSameAs(attached);
    }



    @Test
    void plannerSkipsUnavailableAndProductsWithoutRoomTags() {
        Product unavailableSofa = product("sofa-unavailable", "Nedostupan kauč", "IKEA", "sofa", 300, 5.0);
        unavailableSofa.setAvailabilityStatus("unavailable");
        unavailableSofa.setInStock(false);

        Product sofaWithoutRoom = product("sofa-no-room", "Kauč bez prostorije", "IKEA", "sofa", 280, 4.9);
        sofaWithoutRoom.setRoomTags("");

        Product limitedSofa = product("sofa-limited", "Provjeri kauč", "JYSK", "sofa", 420, 4.1);
        limitedSofa.setAvailabilityStatus("limited");
        limitedSofa.setInStock(true);

        PlannerService service = serviceWithProducts(List.of(unavailableSofa, sofaWithoutRoom, limitedSofa));

        FurnishingPlanDto plan = service.generate(input("Imam 1500 € za dnevni boravak."))
                .plans()
                .get(0);

        assertThat(plan.items()).extracting(item -> item.product().id()).contains("sofa-limited");
        assertThat(plan.items()).extracting(item -> item.product().id()).doesNotContain("sofa-unavailable", "sofa-no-room");
    }

    @Test
    void storeTripSummarisesStoresAndItemsToCheckInStore() {
        Product sofa = product("sofa-ikea", "Kauč svijetli", "IKEA", "sofa", 650, 4.6);
        Product tv = product("tv-ikea", "TV komoda", "IKEA", "tv-unit", 180, 4.4);
        Product table = product("table-jysk", "Stolić", "JYSK", "table", 90, 4.2);
        table.setAvailabilityStatus("limited");

        PlannerService service = serviceWithProducts(List.of(sofa, tv, table));

        FurnishingPlanDto plan = service.generate(input("Imam 1500 € za dnevni boravak."))
                .plans()
                .get(0);

        StoreTripDto trip = plan.storeTrip();
        assertThat(trip).isNotNull();
        assertThat(trip.storeCount()).isEqualTo(plan.retailersUsed().size());
        assertThat(trip.stores()).extracting(StoreTotalDto::retailer)
                .containsExactlyInAnyOrderElementsOf(plan.retailersUsed());
        assertThat(trip.mainRetailer()).isEqualTo("IKEA");
        assertThat(trip.checkInStoreCount()).isEqualTo(1);
        assertThat(trip.recommendation()).contains("provjeri u trgovini");
    }

    @Test
    void excludedCategoryIsSkippedAndRequestedCategoryStaysInPlan() {
        Product sofa = product("sofa-1", "Kauč", "IKEA", "sofa", 600, 4.6);
        Product tv = product("tv-1", "TV komoda", "IKEA", "tv-unit", 300, 4.4);
        Product rug = product("rug-1", "Tepih", "IKEA", "rug", 120, 4.3);
        PlannerService service = serviceWithProducts(List.of(sofa, tv, rug));

        FurnishingPlanDto plan = service.generate(input("Imam 1500 € za dnevni boravak, bez tepiha, treba mi kauč."))
                .plans()
                .get(0);

        assertThat(categories(plan)).contains("sofa");
        assertThat(categories(plan)).doesNotContain("rug");
    }

    @Test
    void storeLimitKeepsPlanInFewerStoresWhenThereIsAReasonableAlternative() {
        Product sofaIkea = product("sofa-ikea", "Kauč", "IKEA", "sofa", 600, 4.6);
        Product tvIkea = product("tv-ikea", "TV komoda IKEA", "IKEA", "tv-unit", 300, 4.3);
        Product tvJysk = product("tv-jysk", "TV komoda JYSK", "JYSK", "tv-unit", 250, 4.7);
        PlannerService service = serviceWithProducts(List.of(sofaIkea, tvIkea, tvJysk));

        FurnishingPlanDto plan = service.generate(input("Imam 1500 € za dnevni boravak, jedna trgovina ako može."))
                .plans()
                .get(0);

        assertThat(plan.retailersUsed()).containsExactly("IKEA");
        assertThat(plan.storeTrip().storeCount()).isEqualTo(1);
    }

    @Test
    void budgetRepairKeepsCoreEvenWhenItExceedsBudget() {
        Product sofa = product("sofa-1", "Kauč", "IKEA", "sofa", 600, 4.6);
        Product tv = product("tv-1", "TV komoda", "IKEA", "tv-unit", 300, 4.4);
        PlannerService service = serviceWithProducts(List.of(sofa, tv));

        FurnishingPlanDto stretch = service.generate(input("Imam 700 € za dnevni boravak."))
                .plans()
                .get(2);

        assertThat(categories(stretch)).contains("sofa", "tv-unit");
        assertThat(stretch.overBudgetAmount()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(stretch.budgetRepairSuggestions()).isNotEmpty();
    }

    @Test
    void budgetRepairMovesOptionalOutOfTheMainBuy() {
        Product sofa = product("sofa-1", "Kauč", "IKEA", "sofa", 600, 4.6);
        Product tv = product("tv-1", "TV komoda", "IKEA", "tv-unit", 250, 4.4);
        Product decor = product("decor-1", "Dekoracije", "IKEA", "decor", 300, 4.0);
        PlannerService service = serviceWithProducts(List.of(sofa, tv, decor));

        FurnishingPlanDto stretch = service.generate(input("Imam 1000 € za dnevni boravak, kompletno."))
                .plans()
                .get(2);

        assertThat(categories(stretch)).contains("sofa", "tv-unit");
        assertThat(categories(stretch)).doesNotContain("decor");
        assertThat(stretch.total().doubleValue()).isLessThanOrEqualTo(1000);
    }

    @Test
    void focusedCrossRoomRequestReturnsEveryNamedItemNotJustTheInferredRoom() {
        // Sprint 10.156: a user names items that SPAN rooms — a bed (bedroom) plus a sofa and a coffee table
        // (living-room). In focused mode each named category is an explicit ask, so ALL must come back; the old
        // code inferred a single room from the first item (bed -> bedroom) and dropped the sofa/table.
        Product bed = product("bed-1", "Krevet", "IKEA", "bed", 300, 4.5);
        bed.setRoomTags("bedroom");
        Product sofa = product("sofa-1", "Kauč", "IKEA", "sofa", 400, 4.6);
        sofa.setRoomTags("living-room");
        Product table = product("table-1", "Stolić za kavu", "IKEA", "table", 90, 4.2);
        table.setRoomTags("living-room");
        PlannerService service = serviceWithProducts(List.of(bed, sofa, table));

        PlannerInputDto focused = input("krevet, kauc i stolic za kavu")
                .withCategories(List.of("bed", "sofa", "table"), List.of());
        FurnishingPlanDto value = service.generateResolved(focused, true).plans().get(0);

        assertThat(categories(value)).contains("bed", "sofa", "table");
    }

    @Test
    void plannerPrefersFresherCompleteProductOverStalePartialWhenSimilar() {
        Product fresh = product("sofa-fresh", "Kauč svjež", "IKEA", "sofa", 600, 4.5);
        fresh.setDataQuality("complete");
        fresh.setLastCheckedAt(java.time.LocalDate.now().toString());

        Product stale = product("sofa-stale", "Kauč star", "IKEA", "sofa", 600, 4.5);
        stale.setDataQuality("partial");
        stale.setProductUrl("");
        stale.setUrl("");
        stale.setImageUrl("");
        stale.setImage("");
        stale.setLastCheckedAt("2020-01-01");

        PlannerService service = serviceWithProducts(List.of(stale, fresh));

        FurnishingPlanDto plan = service.generate(input("Imam 1500 € za dnevni boravak."))
                .plans()
                .get(0);

        assertThat(plan.items()).extracting(item -> item.product().id()).contains("sofa-fresh");
        assertThat(plan.items()).extracting(item -> item.product().id()).doesNotContain("sofa-stale");
    }

    @Test
    void staleProductStillEntersPlan() {
        Product stale = product("sofa-old", "Kauč stariji", "IKEA", "sofa", 500, 4.4);
        stale.setLastCheckedAt("2020-01-01");
        stale.setDataQuality("partial");

        PlannerService service = serviceWithProducts(List.of(stale));

        FurnishingPlanDto plan = service.generate(input("Imam 1500 € za dnevni boravak."))
                .plans()
                .get(0);

        assertThat(plan.items()).extracting(item -> item.product().id()).contains("sofa-old");
    }

    @Test
    void needsReviewProductDoesNotEnterPlan() {
        Product good = product("sofa-good", "Kauč dobar", "IKEA", "sofa", 500, 4.5);
        Product review = product("sofa-review", "Kauč za pregled", "IKEA", "sofa", 400, 4.6);
        review.setDataQuality("needs-review");

        PlannerService service = serviceWithProducts(List.of(review, good));

        FurnishingPlanDto plan = service.generate(input("Imam 1500 € za dnevni boravak."))
                .plans()
                .get(0);

        assertThat(plan.items()).extracting(item -> item.product().id()).contains("sofa-good");
        assertThat(plan.items()).extracting(item -> item.product().id()).doesNotContain("sofa-review");
    }

    @Test
    void planIsPartialAndDoesNotInventProductsWhenRequiredCategoryMissing() {
        Product tv = product("tv-1", "TV komoda", "IKEA", "tv-unit", 200, 4.5);
        PlannerService service = serviceWithProducts(List.of(tv));

        PlanGenerationResponse response = service.generate(input("Imam 1500 € za dnevni boravak."));

        assertThat(response.partialPlan()).isTrue();
        assertThat(response.missingImportantCategories()).contains("sofa");
        assertThat(response.catalogWarning()).contains("kombinacija").doesNotContain("catalog");
        assertThat(response.plans().get(0).items())
                .extracting(item -> item.product().category())
                .doesNotContain("sofa");
    }

    @Test
    void scoringPrefersProductMatchingColorAndMaterialPreferences() {
        Product green = product("sofa-green", "Kauč zeleni", "IKEA", "sofa", 600, 4.5);
        green.setColorTags("green");
        green.setMaterialTags("wood");
        Product grey = product("sofa-grey", "Kauč sivi", "IKEA", "sofa", 600, 4.5);
        grey.setColorTags("grey");
        grey.setMaterialTags("metal");
        PlannerService service = serviceWithProducts(List.of(grey, green));

        FurnishingPlanDto plan = service.generate(input("Imam 1500 € za dnevni boravak, želim zelene detalje i drvo."))
                .plans()
                .get(0);

        assertThat(plan.items()).extracting(item -> item.product().id()).contains("sofa-green");
        assertThat(plan.items()).extracting(item -> item.product().id()).doesNotContain("sofa-grey");
    }

    @Test
    void colorBonusDoesNotOverrideStockOrStyle() {
        // A colour/material match must not pull an out-of-stock product into the plan.
        Product matchingButOutOfStock = product("sofa-match-oos", "Kauč zeleni drveni", "IKEA", "sofa", 600, 4.9);
        matchingButOutOfStock.setColorTags("green");
        matchingButOutOfStock.setMaterialTags("wood");
        matchingButOutOfStock.setAvailabilityStatus("unavailable");
        matchingButOutOfStock.setInStock(false);
        Product plainInStock = product("sofa-plain", "Kauč sivi", "IKEA", "sofa", 600, 4.4);

        PlannerService service = serviceWithProducts(List.of(matchingButOutOfStock, plainInStock));

        FurnishingPlanDto plan = service.generate(input("Imam 1500 € za dnevni boravak, želim zeleno i drvo."))
                .plans()
                .get(0);

        assertThat(plan.items()).extracting(item -> item.product().id()).contains("sofa-plain");
        assertThat(plan.items()).extracting(item -> item.product().id()).doesNotContain("sofa-match-oos");
    }

    @Test
    void plannerOnlyUsesProductsForTheRequestedMarketAndGlobalProducts() {
        Product hr = product("sofa-hr", "Kauč HR", "IKEA", "sofa", 600, 4.5);
        hr.setMarket("HR");
        Product si = product("sofa-si", "Kauč SI", "IKEA", "sofa", 500, 4.6);
        si.setMarket("SI");
        Product global = product("tv-global", "TV global", "IKEA", "tv-unit", 200, 4.4);
        // global.market stays null → matches any market
        PlannerService service = serviceWithProducts(List.of(hr, si, global));

        FurnishingPlanDto hrPlan = service.generate(input("Imam 1500 € za dnevni boravak.").withMarket("HR")).plans().get(0);
        assertThat(hrPlan.items()).extracting(item -> item.product().id()).contains("sofa-hr", "tv-global").doesNotContain("sofa-si");

        FurnishingPlanDto siPlan = service.generate(input("Imam 1500 € za dnevni boravak.").withMarket("SI")).plans().get(0);
        assertThat(siPlan.items()).extracting(item -> item.product().id()).contains("sofa-si", "tv-global").doesNotContain("sofa-hr");
    }

    @Test
    void sampleProductWithoutSourceReferenceIsExcludedFromPlan() {
        // Verified-only gate (production integrity): a legacy data.sql-style row (no sourceReference) must
        // never reach a user — even when it would otherwise win on price + rating — while an
        // otherwise-weaker but properly-sourced product is served instead.
        Product sample = product("sofa-sample", "Kauč bez izvora", "IKEA", "sofa", 480, 4.9);
        sample.setSourceReference(null);   // legacy sample: no provenance
        Product sourced = product("sofa-sourced", "Kauč provjereni", "IKEA", "sofa", 520, 4.4);

        PlannerService service = serviceWithProducts(List.of(sample, sourced));
        FurnishingPlanDto plan = service.generate(input("Imam 1500 € za dnevni boravak.")).plans().get(0);

        assertThat(plan.items()).extracting(item -> item.product().id()).contains("sofa-sourced");
        assertThat(plan.items()).extracting(item -> item.product().id()).doesNotContain("sofa-sample");
    }

    // Sprint 10.158 (Move-In fill): a room whose catalog can only absorb a fraction of its weighted share must
    // not strand the rest — the allocator caps it at its catalog capacity and moves the excess to a room that
    // can actually spend it. Here the kitchen sells ONE 90€ cart, so its naive ~1177€ share flows to the
    // living room (whose own cap is its priciest sofa+tv, 1500€).
    @Test
    void moveInCapsAThinRoomAndMovesItsBudgetToARoomThatCanSpendIt() {
        Product sofaCheap = product("sofa-s", "Kauč mali", "IKEA", "sofa", 200, 4.0);
        Product sofaMid = product("sofa-m", "Kauč srednji", "IKEA", "sofa", 600, 4.2);
        Product sofaBig = product("sofa-l", "Kauč veliki", "IKEA", "sofa", 1200, 4.5);
        Product tv = product("tv-s", "TV klupa", "IKEA", "tv-unit", 100, 4.0);
        Product tvMid = product("tv-m", "TV regal", "IKEA", "tv-unit", 300, 4.1);
        Product cart = product("cart-1", "Kuhinjska kolica", "IKEA", "kitchen-cart", 90, 4.0);
        cart.setRoomTags("kitchen");
        PlannerService service = serviceWithProducts(List.of(sofaCheap, sofaMid, sofaBig, tv, tvMid, cart));

        MoveInResponse response = service.generateMoveIn(
                new MoveInRequestDto(null, List.of("living-room", "kitchen"), 3000));

        MoveInRoomDto living = response.rooms().get(0);
        MoveInRoomDto kitchen = response.rooms().get(1);
        assertThat(kitchen.allocatedBudget())
                .as("the kitchen is capped near its 90€ catalog capacity (+ tier headroom), not handed its ~1177€ weighted share")
                .isLessThanOrEqualTo(150);
        assertThat(living.allocatedBudget())
                .as("the kitchen's excess moves to the living room, up to ITS catalog capacity")
                .isGreaterThanOrEqualTo(1400);
        assertThat(response.grandTotal().intValue()).isLessThanOrEqualTo(3000);
        assertThat(kitchen.plans().get(0).items())
                .extracting(item -> item.product().category())
                .contains("kitchen-cart");
    }

    // Sprint 10.161 (from the whole-apartment scenario sweep): a Move-In shopper who already owns a category
    // must NOT be told to re-buy it. The move-in path used to drop base.alreadyHaveCategories (hardcoded empty),
    // so an owned sofa/bed was re-recommended in every market — the sweep's top trust-killer.
    @Test
    void moveInHonorsAlreadyOwnedCategories() {
        PlannerService service = serviceWithProducts(defaultProducts());
        PlannerInputDto base = new PlannerInputDto("", 1500, "living-room", "bright", "Zagreb", 20, "multi", null,
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0)
                .withCategories(List.of(), List.of("sofa"));

        MoveInResponse response = service.generateMoveIn(new MoveInRequestDto(base, List.of("living-room"), 3000));

        List<String> cats = categories(response.rooms().get(0).plans().get(0));
        assertThat(cats).as("an already-owned sofa must not be re-recommended in Move-In").doesNotContain("sofa");
        assertThat(cats).as("the rest of the room is still planned").contains("tv-unit");
    }

    // ---- Move-In adjust: reduce total / fewer stores / use remaining (Sprint 10.183) ----

    @Test
    void adjustReduceTotalSwapsThePriciestNonKeptItemAndLeavesTheKeptRoomUntouched() {
        Product sofaPrem = product("sofa-prem", "Kauč lux", "IKEA", "sofa", 900, 4.6);
        Product sofaBasic = product("sofa-basic", "Kauč osnovni", "IKEA", "sofa", 300, 4.0);
        Product tv = product("tv-1", "TV komoda", "IKEA", "tv-unit", 200, 4.3);
        Product bed = product("bed-1", "Krevet", "IKEA", "bed", 500, 4.4);
        PlannerService service = serviceWithProducts(List.of(sofaPrem, sofaBasic, tv, bed));

        FurnishingPlanDto living = planOf("value", ProductDto.from(sofaPrem), ProductDto.from(tv)); // 1100
        FurnishingPlanDto bedroom = planOf("value", ProductDto.from(bed));                          // 500 (kept)

        MoveInResponse resp = service.adjustMoveIn(new MoveInAdjustRequest(baseHR(),
                List.of(new AdjustRoomDto("living-room", living, false, List.of()),
                        new AdjustRoomDto("bedroom", bedroom, true, List.of())),
                3000, "reduce-total", 1100, Map.of()));

        assertThat(resp.changed()).isTrue();
        assertThat(resp.grandTotal().intValue()).as("hits the target (kept bedroom 500 + reduced living 500)").isLessThanOrEqualTo(1100);
        List<String> livingIds = resp.rooms().get(0).plans().get(0).items().stream().map(i -> i.product().id()).toList();
        assertThat(livingIds).as("priciest sofa swapped down, tv untouched").contains("sofa-basic", "tv-1").doesNotContain("sofa-prem");
        assertThat(resp.rooms().get(1).plans().get(0).total()).as("kept room untouched").isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(resp.rooms().get(1).plans().get(0).items().get(0).product().id()).isEqualTo("bed-1");
    }

    @Test
    void adjustReduceTotalNeverSwapsAKeptProduct() {
        Product sofaPrem = product("sofa-prem", "Kauč lux", "IKEA", "sofa", 900, 4.6);
        Product tvPrem = product("tv-prem", "TV lux", "IKEA", "tv-unit", 400, 4.5);
        Product tvBasic = product("tv-basic", "TV osnovni", "IKEA", "tv-unit", 150, 4.0);
        PlannerService service = serviceWithProducts(List.of(sofaPrem, tvPrem, tvBasic));

        FurnishingPlanDto living = planOf("value", ProductDto.from(sofaPrem), ProductDto.from(tvPrem)); // 1300

        MoveInResponse resp = service.adjustMoveIn(new MoveInAdjustRequest(baseHR(),
                List.of(new AdjustRoomDto("living-room", living, false, List.of("sofa-prem"))),
                3000, "reduce-total", 1100, Map.of()));

        List<String> ids = resp.rooms().get(0).plans().get(0).items().stream().map(i -> i.product().id()).toList();
        assertThat(ids).as("kept sofa stays; the tv is swapped down instead").contains("sofa-prem", "tv-basic").doesNotContain("tv-prem");
        assertThat(resp.grandTotal().intValue()).isLessThanOrEqualTo(1100);
    }

    @Test
    void adjustReduceTotalIsHonestWhenKeptItemsAlreadyCostMoreThanTheTarget() {
        Product sofaBasic = product("sofa-basic", "Kauč osnovni", "IKEA", "sofa", 300, 4.0);
        Product bed = product("bed-1", "Krevet", "IKEA", "bed", 800, 4.4);
        PlannerService service = serviceWithProducts(List.of(sofaBasic, bed));

        FurnishingPlanDto living = planOf("value", ProductDto.from(sofaBasic)); // 300, adjustable but already cheapest
        FurnishingPlanDto bedroom = planOf("value", ProductDto.from(bed));      // 800, kept

        MoveInResponse resp = service.adjustMoveIn(new MoveInAdjustRequest(baseHR(),
                List.of(new AdjustRoomDto("living-room", living, false, List.of()),
                        new AdjustRoomDto("bedroom", bedroom, true, List.of())),
                3000, "reduce-total", 500, Map.of()));

        assertThat(resp.message()).as("honest: target below the kept items is unreachable").isEqualTo("reduce-unreachable");
        assertThat(resp.grandTotal().intValue()).as("never drops the kept bed").isGreaterThanOrEqualTo(800);
        assertThat(resp.rooms().get(1).plans().get(0).items().get(0).product().id()).isEqualTo("bed-1");
    }

    @Test
    void adjustFewerStoresConsolidatesIntoOneStoreWithinBudget() {
        Product sofaJysk = product("sofa-jysk", "Kauč", "JYSK", "sofa", 400, 4.5);
        Product sofaIkea = product("sofa-ikea", "Kauč", "IKEA", "sofa", 380, 4.7);
        Product tvIkea = product("tv-ikea", "TV", "IKEA", "tv-unit", 200, 4.4);
        PlannerService service = serviceWithProducts(List.of(sofaJysk, sofaIkea, tvIkea));

        FurnishingPlanDto living = planOf("value", ProductDto.from(sofaJysk), ProductDto.from(tvIkea)); // JYSK + IKEA

        MoveInResponse resp = service.adjustMoveIn(new MoveInAdjustRequest(baseHR(),
                List.of(new AdjustRoomDto("living-room", living, false, List.of())),
                3000, "fewer-stores", null, Map.of()));

        List<String> retailers = resp.rooms().get(0).plans().get(0).items().stream().map(i -> i.product().retailer()).distinct().toList();
        assertThat(resp.changed()).isTrue();
        assertThat(retailers).as("consolidated to a single store").hasSize(1);
        assertThat(resp.grandTotal().intValue()).isLessThanOrEqualTo(3000);
    }

    @Test
    void adjustFewerStoresIsHonestWhenAlreadyOneStore() {
        Product sofa = product("sofa-1", "Kauč", "IKEA", "sofa", 400, 4.5);
        Product tv = product("tv-1", "TV", "IKEA", "tv-unit", 200, 4.4);
        PlannerService service = serviceWithProducts(List.of(sofa, tv));

        FurnishingPlanDto living = planOf("value", ProductDto.from(sofa), ProductDto.from(tv)); // IKEA only

        MoveInResponse resp = service.adjustMoveIn(new MoveInAdjustRequest(baseHR(),
                List.of(new AdjustRoomDto("living-room", living, false, List.of())),
                3000, "fewer-stores", null, Map.of()));

        assertThat(resp.changed()).isFalse();
        assertThat(resp.message()).isEqualTo("fewer-stores-noop");
    }

    @Test
    void adjustUseRemainingUpgradesAnItemWithoutExceedingTheBudget() {
        Product sofaBasic = product("sofa-basic", "Kauč", "IKEA", "sofa", 300, 4.0);
        Product sofaNice = product("sofa-nice", "Kauč bolji", "IKEA", "sofa", 450, 4.7);
        Product tv = product("tv-1", "TV", "IKEA", "tv-unit", 200, 4.3);
        PlannerService service = serviceWithProducts(List.of(sofaBasic, sofaNice, tv));

        FurnishingPlanDto living = planOf("value", ProductDto.from(sofaBasic), ProductDto.from(tv)); // 500

        MoveInResponse resp = service.adjustMoveIn(new MoveInAdjustRequest(baseHR(),
                List.of(new AdjustRoomDto("living-room", living, false, List.of())),
                3000, "use-remaining", null, Map.of()));

        assertThat(resp.changed()).isTrue();
        assertThat(resp.message()).isEqualTo("use-remaining-done");
        List<String> ids = resp.rooms().get(0).plans().get(0).items().stream().map(i -> i.product().id()).toList();
        assertThat(ids).as("basic sofa upgraded to the nicer one").contains("sofa-nice");
        assertThat(resp.grandTotal().intValue()).as("never over budget").isLessThanOrEqualTo(3000);
    }

    @Test
    void moveInRoomSurfacesAMarketUnavailableRequiredCategory() {
        // Living room needs a sofa, but the catalog has none -> honest "Nije pronađeno za tvoje tržište" bucket.
        Product tv = product("tv-1", "TV", "IKEA", "tv-unit", 200, 4.3);
        Product table = product("table-1", "Stolić", "IKEA", "table", 90, 4.2);
        PlannerService service = serviceWithProducts(List.of(tv, table));

        MoveInResponse resp = service.generateMoveIn(new MoveInRequestDto(baseHR(), List.of("living-room"), 3000));

        assertThat(resp.rooms().get(0).unavailableInMarket())
                .as("sofa is required for a living room but the market stocks none")
                .contains("sofa");
    }

    private PlannerInputDto baseHR() {
        return new PlannerInputDto("", 1500, "living-room", "bright", "Zagreb", 20, "multi",
                List.of("IKEA", "JYSK", "Pevex", "Emmezeta", "Decathlon", "Lesnina"),
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0);
    }

    private FurnishingPlanDto planOf(String id, ProductDto... products) {
        List<PlanItemDto> items = new ArrayList<>();
        for (ProductDto product : products) items.add(new PlanItemDto(product, "", "buy-first", "", "", 1));
        BigDecimal total = items.stream()
                .map(item -> item.product().price().multiply(BigDecimal.valueOf(Math.max(1, item.quantity()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> retailers = items.stream().map(item -> item.product().retailer()).distinct().toList();
        return new FurnishingPlanDto(id, "Plan", "", "", "", "", "", "", "", "",
                List.of(), List.of(), items, total, BigDecimal.ZERO, 80, "Low", 80, retailers,
                null, null, null, null, null);
    }

    @Test
    void retailerDiversitySpreadsAcrossComparablePiecesWhenEnabled() {
        // Sprint 10.186: with comparable IKEA+JYSK for each slot, the legacy planner picks IKEA every time; the
        // diversity tie-break spreads the plan across retailers (without a forced percentage).
        List<Product> catalog = new ArrayList<>();
        for (String category : List.of("sofa", "rug", "table", "lighting", "storage")) {
            int price = "sofa".equals(category) ? 500 : 120;
            catalog.add(product(category + "-ikea", "IKEA " + category, "IKEA", category, price, 4.6));
            catalog.add(product(category + "-jysk", "JYSK " + category, "JYSK", category, price, 4.5)); // same price, ~same rating
        }
        FurnishingPlanDto legacy = serviceWithProducts(catalog).generate(input("dnevni boravak")).plans().get(0);
        long legacyJysk = legacy.items().stream().filter(item -> "JYSK".equals(item.product().retailer())).count();
        FurnishingPlanDto diverse = serviceWithProducts(catalog).withRetailerDiversity(true)
                .generate(input("dnevni boravak")).plans().get(0);
        long diverseJysk = diverse.items().stream().filter(item -> "JYSK".equals(item.product().retailer())).count();

        assertThat(diverse.items().size()).isGreaterThanOrEqualTo(4);
        assertThat(diverseJysk).as("diversity spreads to comparable JYSK vs the IKEA-dominant legacy").isGreaterThan(legacyJysk);
    }

    @Test
    void retailerDiversityNeverPicksAPricierOrWorsePiece() {
        // The budget tier picks cheapest, so IKEA is the deterministic best here; the JYSK alternatives are a much
        // pricier one and a much worse-rated one — the diversity guards (price band + rating drop) must reject both.
        List<Product> catalog = List.of(
                product("sofa-ikea", "IKEA sofa", "IKEA", "sofa", 500, 4.6),
                product("sofa-jysk", "JYSK sofa pricier", "JYSK", "sofa", 750, 4.6),   // +50% > 15% band
                product("rug-ikea", "IKEA rug", "IKEA", "rug", 120, 4.6),
                product("rug-jysk", "JYSK rug worse", "JYSK", "rug", 120, 3.0));        // same price, far worse rating
        FurnishingPlanDto budget = serviceWithProducts(catalog).withRetailerDiversity(true)
                .generate(input("dnevni boravak")).plans().get(1);

        assertThat(retailerOf(budget, "sofa")).as("a much pricier JYSK is never chosen").isEqualTo("IKEA");
        assertThat(retailerOf(budget, "rug")).as("a much worse-rated JYSK is never chosen").isEqualTo("IKEA");
    }

    @Test
    void retailerDiversityStaysConsolidatedWhenUserWantsFewerStores() {
        List<Product> catalog = new ArrayList<>();
        for (String category : List.of("sofa", "rug", "table")) {
            int price = "sofa".equals(category) ? 500 : 120;
            catalog.add(product(category + "-ikea", "IKEA " + category, "IKEA", category, price, 4.6));
            catalog.add(product(category + "-jysk", "JYSK " + category, "JYSK", category, price, 4.6));
        }
        // "samo jedna trgovina" => maxStores=1 => prefersFewStores => the diversity tie-break is disabled.
        PlannerInputDto fewStores = input("dnevni boravak, samo jedna trgovina");
        long legacyStores = serviceWithProducts(catalog).generate(fewStores).plans().get(0)
                .items().stream().map(item -> item.product().retailer()).distinct().count();
        long diverseStores = serviceWithProducts(catalog).withRetailerDiversity(true).generate(fewStores).plans().get(0)
                .items().stream().map(item -> item.product().retailer()).distinct().count();

        assertThat(diverseStores).as("diversity must NOT widen the store count in a fewer-stores request")
                .isLessThanOrEqualTo(legacyStores);
    }

    @Test
    void onlyJyskInAMarketWithoutJyskRelaxesWithAnHonestNote() {
        // GB has no JYSK; "samo JYSK" must not yield an empty plan — relax to the available stores + an honest note.
        List<Product> catalog = List.of(
                gbProduct("sofa-ikea", "IKEA sofa", "sofa", 500),
                gbProduct("rug-ikea", "IKEA rug", "rug", 120));
        PlannerInputDto onlyJysk = input("dnevni boravak").withMarket("GB")
                .withRetailerIntent("single", List.of("JYSK"), List.of(), List.of(), 0);

        PlanGenerationResponse response = serviceWithProducts(catalog).generate(onlyJysk);

        assertThat(response.plans().get(0).items()).as("the plan is not left empty").isNotEmpty();
        assertThat(response.catalogWarning()).as("an honest note that the store wish couldn't be met here").isNotNull();
    }

    private String retailerOf(FurnishingPlanDto plan, String category) {
        return plan.items().stream()
                .filter(item -> category.equals(item.product().category()))
                .map(item -> item.product().retailer()).findFirst().orElse("");
    }

    private Product gbProduct(String id, String name, String category, int price) {
        Product product = product(id, name, "IKEA", category, price, 4.5);
        product.setMarket("GB");
        return product;
    }

    @Test
    void representativePlannerMatrixWithDiversityStaysHealthyAndSpreads() {
        // Sprint 10.186: run the NEW selection path across a representative room x budget matrix with diversity ON,
        // asserting the invariants the owner cares about — coverage kept, nothing over budget, and JYSK spread where a
        // comparable alternative exists (so the tie-break really fires across scenarios, not just in one unit case).
        List<Product> catalog = new ArrayList<>();
        String[] categories = {"sofa", "tv-unit", "table", "rug", "lighting", "storage", "bed", "mattress",
                "nightstand", "wardrobe", "dresser", "desk", "dining-table", "dining-chair", "chair"};
        for (String category : categories) {
            int price = List.of("sofa", "bed", "wardrobe", "dining-table").contains(category) ? 500 : 130;
            catalog.add(fullRoomProduct(category + "-ikea", "IKEA " + category, "IKEA", category, price, 4.6));
            catalog.add(fullRoomProduct(category + "-jysk", "JYSK " + category, "JYSK", category, price, 4.5));
        }
        PlannerService legacy = serviceWithProducts(catalog);                                  // diversity OFF
        PlannerService diverse = serviceWithProducts(catalog).withRetailerDiversity(true);      // diversity ON

        long jyskTotal = 0;
        for (String room : List.of("living-room", "bedroom", "dining-room", "home-office")) {
            for (int budget : new int[]{1000, 1500, 2500}) {
                FurnishingPlanDto leg = legacy.generate(structuredInput(room, budget)).plans().get(0);
                FurnishingPlanDto div = diverse.generate(structuredInput(room, budget)).plans().get(0);
                // Coverage never drops, and the plan is never made pricier (so no NEW over-budget) by diversity —
                // comparable swaps here are price-neutral, so the diverse total must not exceed the legacy total.
                assertThat(div.items().size()).as(room + "@" + budget + " keeps coverage").isGreaterThanOrEqualTo(leg.items().size());
                assertThat(div.total()).as(room + "@" + budget + " not pricier than legacy").isLessThanOrEqualTo(leg.total());
                jyskTotal += div.items().stream().filter(item -> "JYSK".equals(item.product().retailer())).count();
            }
        }
        assertThat(jyskTotal).as("diversity spread JYSK across the matrix, not IKEA-only everywhere").isGreaterThan(0);
    }

    private PlannerInputDto structuredInput(String room, int budget) {
        return new PlannerInputDto("", budget, room, "modern", "Zagreb", 20, "multi",
                List.of("IKEA", "JYSK", "Pevex", "Emmezeta", "Decathlon", "Lesnina"), "best-value", "comfort",
                List.of(), List.of(), List.of(), List.of(), List.of(), 0);
    }

    private Product fullRoomProduct(String id, String name, String retailer, String category, int price, double rating) {
        Product product = product(id, name, retailer, category, price, rating);
        product.setRoomTags("living-room,home-office,bedroom,dining-room,kitchen,hallway");
        return product;
    }

    private PlannerService serviceWithProducts(List<Product> products) {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(products);
        return new PlannerService(repository);
    }

    private PlannerInputDto input(String prompt) {
        return new PlannerInputDto(
                prompt,
                1500,
                "living-room",
                "modern",
                "Zagreb",
                20,
                "multi",
                List.of("IKEA", "JYSK", "Pevex", "Emmezeta", "Decathlon", "Lesnina"),
                "best-value",
                "comfort",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0
        );
    }

    private List<String> categories(FurnishingPlanDto plan) {
        return plan.items().stream().map(item -> item.product().category()).toList();
    }

    // Sprint 10.183: a minimal plan holding the given products, for replaceProduct tests. The total is derived so
    // replaceProduct's own remainingBudget (budget − everything-but-the-replaced-item) is realistic; the other
    // metadata fields are placeholders the replace path doesn't read.
    private FurnishingPlanDto planWith(Product... products) {
        List<PlanItemDto> items = new ArrayList<>();
        double total = 0;
        for (Product p : products) {
            items.add(new PlanItemDto(ProductDto.from(p), "Komad", "buy-first", "Opis", "1. Najvažnije za početak"));
            total += p.getPrice().doubleValue();
        }
        return new FurnishingPlanDto(
                "value", "Najbolji izbor", "Najbolji omjer", "Opis", "", "", "", "", "", "",
                List.of(), List.of(), items,
                BigDecimal.valueOf(total), BigDecimal.valueOf(Math.max(0, 1500 - total)), 80, "Low", 80,
                List.of("IKEA", "JYSK"), null, null, null, null, null);
    }

    private List<Product> defaultProducts() {
        List<Product> products = new ArrayList<>();
        products.add(product("sofa-1", "Kauč", "IKEA", "sofa", 650, 4.7));
        products.add(product("tv-1", "TV komoda", "IKEA", "tv-unit", 180, 4.5));
        products.add(product("table-1", "Stolić", "IKEA", "table", 90, 4.2));
        products.add(product("rug-1", "Tepih", "JYSK", "rug", 120, 4.3));
        products.add(product("lighting-1", "Lampa", "JYSK", "lighting", 70, 4.4));
        products.add(product("storage-1", "Polica", "IKEA", "storage", 140, 4.1));
        products.add(product("decor-1", "Dekor", "IKEA", "decor", 40, 4.0));
        return products;
    }

    // Sprint 10.178 (B): without a stated colour the plan should self-coordinate the bathroom fixtures to a coherent
    // neutral palette (the "black toilet + white washbasin" bug). Ratings are rigged so that WITHOUT the coherence /
    // neutral-anchor bonus the planner picks a black toilet (4.9) + a white washbasin (4.9) — a mismatch.
    @Test
    void bathroomFixturesCoordinateToANeutralPaletteWhenNoColourStated() {
        PlannerService service = serviceWithProducts(List.of(
                coloredFixture("wc-black", "WC crna", "toilet", "black", 4.9),
                coloredFixture("wc-white", "WC bijela", "toilet", "white", 4.5),
                coloredFixture("basin-white", "Umivaonik bijeli", "washbasin", "white", 4.9),
                coloredFixture("basin-black", "Umivaonik crni", "washbasin", "black", 4.5)));

        FurnishingPlanDto plan = service.generateResolved(bathroomInput(List.of())).plans().get(0);
        List<String> toilet = fixtureColour(plan, "toilet");
        List<String> washbasin = fixtureColour(plan, "washbasin");
        assertThat(toilet).as("toilet colour matches washbasin").isEqualTo(washbasin);  // coherent (not black+white)
        assertThat(toilet).as("neutral default").contains("white");                     // neutral anchor
    }

    @Test
    void anExplicitColourStillDrivesTheFixtureSelection() {
        PlannerService service = serviceWithProducts(List.of(
                coloredFixture("wc-black", "WC crna", "toilet", "black", 4.5),
                coloredFixture("wc-white", "WC bijela", "toilet", "white", 4.9),
                coloredFixture("basin-white", "Umivaonik bijeli", "washbasin", "white", 4.9),
                coloredFixture("basin-black", "Umivaonik crni", "washbasin", "black", 4.5)));

        // "crna" (black) requested → the black fixture wins despite the white one's higher rating + neutral pull.
        FurnishingPlanDto plan = service.generateResolved(bathroomInput(List.of("black"))).plans().get(0);
        assertThat(fixtureColour(plan, "toilet")).as("explicit black toilet").contains("black");
    }

    // ── Sprint 10.179: size (m²) → piece-fit signal ──────────────────────────────────────────────────
    // We have NO structured dimensions — they live only in product NAMES ("KIVIK 3-seat", "MALM 160x200").
    // pieceFit() reads a soft footprint band from the name (cm width, or a multilingual seat count for sofas),
    // but ONLY for footprint categories; every other category, and any name with no signal, is NEUTRAL.

    @Test
    void pieceFitReadsCmWidthForFootprintCategories() {
        // Width = the first number of a WxL(xH) group; a standalone "N cm" also counts. Bands: <130 compact,
        // >190 large, else mid.
        assertThat(PlannerService.pieceFit(product("bed-single", "MALM 90x200", "IKEA", "bed", 300, 4.5)))
                .isEqualTo(PlannerService.PieceFit.COMPACT);                       // width 90 < 130
        assertThat(PlannerService.pieceFit(product("bed-queen", "MALM 160x200", "IKEA", "bed", 400, 4.5)))
                .isEqualTo(PlannerService.PieceFit.MID);                           // width 160, in 130..190
        assertThat(PlannerService.pieceFit(product("vanity", "ÄNGSJÖN 80x48x63", "IKEA", "dresser", 250, 4.5)))
                .isEqualTo(PlannerService.PieceFit.COMPACT);                       // WxDxH → width 80 < 130
        assertThat(PlannerService.pieceFit(product("shelf", "BILLY polica 200 cm", "IKEA", "storage", 120, 4.5)))
                .isEqualTo(PlannerService.PieceFit.LARGE);                         // 200 cm > 190
    }

    @Test
    void pieceFitReadsMultilingualSeatCountForSofasWithoutDimensions() {
        assertThat(PlannerService.pieceFit(product("s1", "KIVIK 2-seat", "IKEA", "sofa", 400, 4.5)))
                .isEqualTo(PlannerService.PieceFit.COMPACT);
        assertThat(PlannerService.pieceFit(product("s2", "KIVIK 3-seat", "IKEA", "sofa", 400, 4.5)))
                .isEqualTo(PlannerService.PieceFit.MID);
        assertThat(PlannerService.pieceFit(product("s3", "VIMLE 2-Sitzer", "IKEA", "sofa", 400, 4.5)))
                .isEqualTo(PlannerService.PieceFit.COMPACT);                       // German
        assertThat(PlannerService.pieceFit(product("s4", "Kauč dvosjed", "JYSK", "sofa", 400, 4.5)))
                .isEqualTo(PlannerService.PieceFit.COMPACT);                       // Croatian word (dvo = 2)
        assertThat(PlannerService.pieceFit(product("s5", "Divano 3 posti", "IKEA", "sofa", 400, 4.5)))
                .isEqualTo(PlannerService.PieceFit.MID);                           // Italian
        assertThat(PlannerService.pieceFit(product("s6", "VIMLE kutna sekcija", "IKEA", "sofa", 900, 4.5)))
                .isEqualTo(PlannerService.PieceFit.LARGE);                         // corner/sectional → large
        assertThat(PlannerService.pieceFit(product("s7", "EKTORP 4-seat", "IKEA", "sofa", 700, 4.5)))
                .isEqualTo(PlannerService.PieceFit.LARGE);                         // 4+ seats → large
    }

    @Test
    void pieceFitIsNeutralWithoutASignalOrForNonFootprintCategories() {
        // No dims, no seat token → neutral.
        assertThat(PlannerService.pieceFit(product("k1", "KALLAX", "IKEA", "storage", 90, 4.5)))
                .isEqualTo(PlannerService.PieceFit.NEUTRAL);
        // A model number / year must NOT be read as a dimension (no 'x' join, no 'cm' unit) → no collision.
        assertThat(PlannerService.pieceFit(product("k2", "IKEA PS 2017", "IKEA", "storage", 90, 4.5)))
                .isEqualTo(PlannerService.PieceFit.NEUTRAL);
        // "2 drawers" is not a seat count.
        assertThat(PlannerService.pieceFit(product("k3", "HEMNES 2 drawers", "IKEA", "dresser", 150, 4.5)))
                .isEqualTo(PlannerService.PieceFit.NEUTRAL);
        // Dims present but the category is not a footprint category → neutral (the band is gated to big pieces).
        assertThat(PlannerService.pieceFit(product("k4", "Zavjesa 140x200", "IKEA", "decor", 30, 4.5)))
                .isEqualTo(PlannerService.PieceFit.NEUTRAL);
        // A seat token only means "size" on a sofa; on another footprint category it is ignored.
        assertThat(PlannerService.pieceFit(product("k5", "Klupa 2-seat", "IKEA", "tv-unit", 100, 4.5)))
                .isEqualTo(PlannerService.PieceFit.NEUTRAL);
    }

    @Test
    void roomBandBucketsBySizeWithSmallAtOrBelow14AndLargeAtOrAbove26() {
        assertThat(PlannerService.roomBand(12)).isEqualTo(PlannerService.RoomBand.SMALL);
        assertThat(PlannerService.roomBand(14)).isEqualTo(PlannerService.RoomBand.SMALL);    // boundary ≤ 14
        assertThat(PlannerService.roomBand(15)).isEqualTo(PlannerService.RoomBand.MEDIUM);
        assertThat(PlannerService.roomBand(20)).isEqualTo(PlannerService.RoomBand.MEDIUM);   // default size
        assertThat(PlannerService.roomBand(25)).isEqualTo(PlannerService.RoomBand.MEDIUM);
        assertThat(PlannerService.roomBand(26)).isEqualTo(PlannerService.RoomBand.LARGE);    // boundary ≥ 26
        assertThat(PlannerService.roomBand(32)).isEqualTo(PlannerService.RoomBand.LARGE);
    }

    // End to end: the room size (m²) is a soft "fit" tie-breaker. Ratings are rigged so that WITHOUT the fit signal
    // the OTHER sofa would win — proving the size signal (not chance) drives each pick. Same price → identical
    // priceBias in every tier, so rating + roomFit are the only differentiators.

    @Test
    void smallRoomPrefersACompactSofaOverALargerOne() {
        // 3-seat is higher-rated → it wins in a medium room; a 12 m² room must flip the pick to the compact 2-seat.
        PlannerService service = serviceWithProducts(twoSofas(4.5, 4.9));
        FurnishingPlanDto plan = service.generateResolved(input("Dnevni boravak.").withSize(12)).plans().get(0);
        assertThat(sofaId(plan)).isEqualTo("sofa-2seat");
    }

    @Test
    void largeRoomPrefersALargerSofaOverACompactOne() {
        // 2-seat is higher-rated → it wins in a medium room; a 32 m² room must flip the pick to the 3-seat.
        PlannerService service = serviceWithProducts(twoSofas(4.9, 4.5));
        FurnishingPlanDto plan = service.generateResolved(input("Dnevni boravak.").withSize(32)).plans().get(0);
        assertThat(sofaId(plan)).isEqualTo("sofa-3seat");
    }

    @Test
    void defaultRoomSizeDoesNotChangeTheSofaPick() {
        // size 20 → MEDIUM → 0 bias → the higher-rated 3-seat wins exactly as before the feature (no regression).
        PlannerService service = serviceWithProducts(twoSofas(4.5, 4.9));
        FurnishingPlanDto plan = service.generateResolved(input("Dnevni boravak.").withSize(20)).plans().get(0);
        assertThat(sofaId(plan)).isEqualTo("sofa-3seat");
    }

    private List<Product> twoSofas(double rating2seat, double rating3seat) {
        // Identical except the name (seat count) and rating, and the SAME price so priceBias is identical in every
        // tier — the only differentiators left are rating and the roomFit bias.
        return List.of(
                product("sofa-2seat", "KIVIK 2-seat", "IKEA", "sofa", 400, rating2seat),
                product("sofa-3seat", "KIVIK 3-seat", "IKEA", "sofa", 400, rating3seat));
    }

    private String sofaId(FurnishingPlanDto plan) {
        return plan.items().stream()
                .filter(item -> "sofa".equals(item.product().category()))
                .map(item -> item.product().id())
                .findFirst().orElse(null);
    }

    // ── Sprint 10.179: utility rooms (garage / pantry / laundry / attic / basement) ──────────────────────
    // These draw from the shared storage/lighting pool via ROOM_CATALOG_TAGS (like studio), so an unsupported
    // "garaža" no longer silently becomes a living-room plan (sofa + TV). generateResolved bypasses the prompt
    // extractor, so the roomType we set is authoritative. Synthetic products carry the default roomTags
    // (living-room,home-office,bedroom,home-gym), which the new rooms' catalog tags overlap, so they are eligible.

    @Test
    void garageIsFurnishedWithShelvingAndAWorkbenchNotASofa() {
        PlannerService service = serviceWithProducts(List.of(
                product("shelf-1", "Regal BROR", "IKEA", "storage", 120, 4.5),
                product("desk-1", "Radni stol", "IKEA", "desk", 150, 4.5),
                product("sofa-1", "Kauč", "IKEA", "sofa", 400, 4.7)));
        FurnishingPlanDto plan = service.generateResolved(input("Opremi garažu.").withRoomType("garage")).plans().get(0);
        assertThat(categories(plan)).contains("storage", "desk");
        assertThat(categories(plan)).doesNotContain("sofa");   // a garage is not a living room
    }

    @Test
    void pantryIsFurnishedWithShelving() {
        PlannerService service = serviceWithProducts(List.of(
                product("shelf-1", "Polica IVAR", "IKEA", "storage", 90, 4.5),
                product("sofa-1", "Kauč", "IKEA", "sofa", 400, 4.7)));
        FurnishingPlanDto plan = service.generateResolved(input("Opremi ostavu.").withRoomType("pantry")).plans().get(0);
        assertThat(categories(plan)).contains("storage");
        assertThat(categories(plan)).doesNotContain("sofa");
    }

    @Test
    void atticAndBasementAreFurnishedFromTheSharedStoragePool() {
        PlannerService service = serviceWithProducts(List.of(
                product("shelf-1", "Regal", "IKEA", "storage", 90, 4.5),
                product("lamp-1", "Lampa", "IKEA", "lighting", 40, 4.5),
                product("sofa-1", "Kauč", "IKEA", "sofa", 400, 4.7)));
        for (String room : List.of("attic", "basement")) {
            FurnishingPlanDto plan = service.generateResolved(input("x").withRoomType(room)).plans().get(0);
            assertThat(categories(plan)).as(room + " has shelving").contains("storage");
            assertThat(categories(plan)).as(room + " is not a living room").doesNotContain("sofa");
        }
    }

    @Test
    void laundryRoomShowsARealBasketInEveryTierNotJustTheCheapest() {
        // Live, the value/stretch "spend-up" slot picked a pricey bathroom mirror cabinet over the €7 basket — the
        // laundry room read like a mini-bathroom. Utility rooms are FUNCTIONAL, not splurge rooms, so they skip the
        // spend-up target and a real laundry basket shows in EVERY tier, not only the cheapest one.
        Product basket = product("basket-1", "TORKIS košara za rublje", "IKEA", "storage", 8, 4.4);
        basket.setRoomTags("bathroom,laundry");
        Product cabinet = product("cabinet-1", "FAXÄLVEN ormarić s ogledalom", "IKEA", "storage", 289, 4.8);
        cabinet.setRoomTags("bathroom");   // reachable in the laundry pool, pricier + higher-rated
        PlannerService service = serviceWithProducts(List.of(cabinet, basket));
        for (FurnishingPlanDto plan : service.generateResolved(input("Opremi praonicu.").withRoomType("laundry")).plans()) {
            List<String> ids = plan.items().stream().map(item -> item.product().id()).toList();
            assertThat(ids).as(plan.name() + " shows the basket").contains("basket-1");
        }
    }

    @Test
    void utilityRoomsStayFunctionalAndNeverSplurgeOverBudget() {
        // Regression: a utility room's "Ljepša verzija" (stretch) tier must not reach for a €2000 designer bookcase
        // over a functional shelf (which blew a €1000 garage 2× over live). Utility rooms lean functional in EVERY tier.
        Product shelf = product("shelf-1", "Regal", "IKEA", "storage", 60, 4.5);
        Product splurge = product("splurge-1", "Biblioteka HERITAGE", "IKEA", "storage", 2000, 4.9);
        PlannerService service = serviceWithProducts(List.of(shelf, splurge));
        for (FurnishingPlanDto plan : service.generateResolved(input("Opremi garažu.").withRoomType("garage")).plans()) {
            List<String> ids = plan.items().stream().map(item -> item.product().id()).toList();
            assertThat(ids).as(plan.name() + " stays functional").contains("shelf-1").doesNotContain("splurge-1");
        }
    }

    private List<String> fixtureColour(FurnishingPlanDto plan, String category) {
        return plan.items().stream()
                .filter(item -> category.equals(item.product().category()))
                .map(item -> item.product().colorTags())
                .findFirst().orElse(List.of());
    }

    private Product coloredFixture(String id, String name, String category, String colour, double rating) {
        Product product = product(id, name, "IKEA", category, 200, rating);
        product.setRoomTags("bathroom");
        product.setColorTags(colour);
        return product;
    }

    private PlannerInputDto bathroomInput(List<String> colourPreferences) {
        return new PlannerInputDto("kupaonica", 1500, "bathroom", "modern", "Zagreb", 20, "multi", List.of(),
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0,
                colourPreferences, List.of(), "HR");
    }

    private Product product(String id, String name, String retailer, String category, int price, double rating) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setRetailer(retailer);
        product.setCategory(category);
        product.setPrice(BigDecimal.valueOf(price));
        product.setOriginalPrice(null);
        product.setStyleTags("modern,scandinavian");
        product.setRoomTags("living-room,home-office,bedroom,home-gym");
        product.setImage("https://example.com/image.jpg");
        product.setUrl("https://example.com/product");
        product.setImageUrl("https://example.com/image.jpg");
        product.setProductUrl("https://example.com/product");
        product.setAvailabilityStatus("in-stock");
        product.setDeliveryNote("Provjeri dostavu ili preuzimanje prije kupnje.");
        product.setLastCheckedAt("2026-06-12");
        product.setExternalId(id);
        product.setPriceTier(price <= 120 ? "budget" : price >= 450 ? "premium" : "standard");
        product.setRating(rating);
        product.setInStock(true);
        product.setNote("Dobar omjer cijene i korisnosti.");
        // Sprint 10.21+: the planner is now verified-only (CatalogSourcePolicy.isPlannerEligible), so a
        // test product needs a real provenance to be selectable — mirrors an imported, web-verified row.
        product.setSourceReference("test-catalog");
        product.setSourceType("public-product-page");
        return product;
    }

    // ---- Move-In cross-room colour coherence (Sprint 10.182) ----

    @Test
    void moveInCoordinatesColoursAcrossRooms() {
        // Living room: the only sofa is BLUE, so it seeds the apartment palette with "blue".
        Product blueSofa = product("sofa-blue", "Plavi kauč", "IKEA", "sofa", 500, 4.6);
        blueSofa.setRoomTags("living-room"); blueSofa.setColorTags("blue");
        Product tv = product("tv-1", "TV komoda", "IKEA", "tv-unit", 200, 4.6);
        tv.setRoomTags("living-room");
        // Bedroom: two otherwise-identical beds. GREEN has the rating edge (wins on its own); the +12 colour
        // coherence for BLUE, seeded from the living room, must flip the whole-apartment pick to BLUE.
        Product greenBed = product("bed-green", "Zeleni krevet", "IKEA", "bed", 500, 4.7);
        greenBed.setRoomTags("bedroom"); greenBed.setColorTags("green");
        Product blueBed = product("bed-blue", "Plavi krevet", "IKEA", "bed", 500, 4.5);
        blueBed.setRoomTags("bedroom"); blueBed.setColorTags("blue");
        Product mattress = product("mattress-1", "Madrac", "IKEA", "mattress", 200, 4.6);
        mattress.setRoomTags("bedroom");

        PlannerService service = serviceWithProducts(List.of(blueSofa, tv, greenBed, blueBed, mattress));

        // Standalone bedroom (no cross-room seed) → the higher-rated GREEN bed wins. Guards single-room behaviour.
        FurnishingPlanDto solo = service.generate(bedroomInput()).plans().get(0);
        assertThat(bedId(solo)).as("standalone bedroom keeps the higher-rated green bed").isEqualTo("bed-green");

        // Whole apartment: the living room settles on blue, so the bedroom coordinates to the BLUE bed.
        MoveInResponse moveIn = service.generateMoveIn(
                new MoveInRequestDto(input(""), List.of("living-room", "bedroom"), 4000));
        FurnishingPlanDto bedroom = moveIn.rooms().stream()
                .filter(room -> room.roomType().equals("bedroom")).findFirst().orElseThrow()
                .plans().get(0);
        assertThat(bedId(bedroom)).as("Move-In bedroom coordinates to the living room's blue").isEqualTo("bed-blue");
    }

    private PlannerInputDto bedroomInput() {
        return input("").withRoomType("bedroom");
    }

    private static String bedId(FurnishingPlanDto plan) {
        return plan.items().stream()
                .filter(item -> "bed".equals(item.product().category()))
                .map(item -> item.product().id())
                .findFirst().orElse(null);
    }
}
