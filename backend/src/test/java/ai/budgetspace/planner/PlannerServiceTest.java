package ai.budgetspace.planner;

import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.dto.MoveInRequestDto;
import ai.budgetspace.dto.MoveInResponse;
import ai.budgetspace.dto.MoveInRoomDto;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlanItemDto;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.ProductDto;
import ai.budgetspace.dto.ReplaceProductRequest;
import ai.budgetspace.dto.StoreTotalDto;
import ai.budgetspace.dto.StoreTripDto;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
}
