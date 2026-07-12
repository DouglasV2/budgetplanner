package ai.budgetspace.product;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTaxonomyTest {
    // Sprint 10.178: colour derivation must be MULTILINGUAL (product names are localized in 15 markets) and must not
    // collide (short colour stems inside unrelated words). Underpins the plan colour-coherence coordination.
    @Test
    void derivesColoursFromCroatianAndEnglishNames() {
        assertThat(ProductTaxonomy.deriveColorTags("Kauč bijela tkanina")).contains("white");
        assertThat(ProductTaxonomy.deriveColorTags("Stolica crna")).contains("black");
        assertThat(ProductTaxonomy.deriveColorTags("WC školjka Stil Remi Rimless black viseća")).contains("black");
        assertThat(ProductTaxonomy.deriveColorTags("Ormar hrast efekt")).contains("natural");
    }

    @Test
    void derivesColoursFromOtherMarketLanguages() {
        assertThat(ProductTaxonomy.deriveColorTags("KABOMBA Wandleuchte, weiß")).contains("white");
        assertThat(ProductTaxonomy.deriveColorTags("STENABY forno termoventilato, nero")).contains("black");
        assertThat(ProductTaxonomy.deriveColorTags("Miroir NISSEDAL noir")).contains("black");
        assertThat(ProductTaxonomy.deriveColorTags("ENHET Waschbeckenschrank, grau")).contains("grey");
        assertThat(ProductTaxonomy.deriveColorTags("ÄNGSJÖN element, Eichenachbildung")).contains("natural");
        assertThat(ProductTaxonomy.deriveColorTags("Armoire chêne")).contains("natural");
        assertThat(ProductTaxonomy.deriveColorTags("Mueble blanco")).contains("white");
        assertThat(ProductTaxonomy.deriveColorTags("MATÄLSKARE mikroaaltouuni, musta")).contains("black");
    }

    // Sprint 10.179: the laundry room surfaces real laundry baskets/hampers, detected by NAME (multilingual, like
    // deriveColorTags). Must NOT fire on a washbasin ("Waschbecken") or a plain shelf.
    @Test
    void detectsLaundryItemsByNameWithoutCollisions() {
        assertThat(ProductTaxonomy.isLaundryItem("TORKIS košara za rublje")).isTrue();       // HR
        assertThat(ProductTaxonomy.isLaundryItem("BRANÄS Wäschekorb")).isTrue();              // DE (Wäsche → wasche)
        assertThat(ProductTaxonomy.isLaundryItem("Laundry basket")).isTrue();                 // EN
        assertThat(ProductTaxonomy.isLaundryItem("KALLAX regal")).isFalse();                  // a plain shelf
        assertThat(ProductTaxonomy.isLaundryItem("ENHET Waschbeckenschrank")).isFalse();      // washbasin cabinet, not laundry
    }

    @Test
    void doesNotFalselyDeriveColoursFromLookalikeWordsOrRangeNames() {
        // A colour stem must not fire from INSIDE an unrelated word / IKEA range name.
        // "blumenförmig" (flower-shaped) must NOT read as blue; a black product must not also read as blue.
        assertThat(ProductTaxonomy.deriveColorTags("SKOGSDUVA Kissen, blumenförmig/weiß"))
                .contains("white").doesNotContain("blue");
        assertThat(ProductTaxonomy.deriveColorTags("BLACK trosjed"))
                .contains("black").doesNotContain("blue");
        // IKEA range names that begin like a colour word must not be mis-derived.
        assertThat(ProductTaxonomy.deriveColorTags("GRISSLAN kanta")).doesNotContain("grey");
        assertThat(ProductTaxonomy.deriveColorTags("VITTSJÖ klupa")).doesNotContain("white");
    }

    @Test
    void mapsCategorySynonymsWithoutMixingChairAndCoffeeTable() {
        assertThat(ProductTaxonomy.normalizeCategory("kauč")).contains("sofa");
        assertThat(ProductTaxonomy.normalizeCategory("tv komoda")).contains("tv-unit");
        assertThat(ProductTaxonomy.normalizeCategory("komoda za tv")).contains("tv-unit");
        assertThat(ProductTaxonomy.normalizeCategory("stolić")).contains("table");
        assertThat(ProductTaxonomy.normalizeCategory("stolic")).contains("table");
        assertThat(ProductTaxonomy.normalizeCategory("stolica")).contains("chair");
        assertThat(ProductTaxonomy.normalizeCategory("stol")).contains("desk");
        assertThat(ProductTaxonomy.normalizeCategory("oprema za vježbanje")).contains("gym-equipment");
    }

    @Test
    void kitchenSetIsAKnownCategoryWithModularAliases() {
        assertThat(ProductTaxonomy.isKnownCategory("kitchen-set")).isTrue();
        assertThat(ProductTaxonomy.normalizeCategory("modular-kitchen")).contains("kitchen-set");
        assertThat(ProductTaxonomy.normalizeCategory("kompletna kuhinja")).contains("kitchen-set");
        // A modular kitchen set must NOT collapse into the freestanding kitchen-storage/cart categories.
        assertThat(ProductTaxonomy.normalizeCategory("kitchen-set")).contains("kitchen-set");
    }

    @Test
    void kitchenAppliancesAreKnownCategories() {
        assertThat(ProductTaxonomy.isKnownCategory("oven")).isTrue();
        assertThat(ProductTaxonomy.isKnownCategory("fridge")).isTrue();
        assertThat(ProductTaxonomy.isKnownCategory("dishwasher")).isTrue();
        assertThat(ProductTaxonomy.normalizeCategory("pecnica")).contains("oven");
        assertThat(ProductTaxonomy.normalizeCategory("hladnjak")).contains("fridge");
        assertThat(ProductTaxonomy.normalizeCategory("perilica posuda")).contains("dishwasher");
    }

    @Test
    void mapsStyleSynonymsToPlannerFriendlyValues() {
        assertThat(ProductTaxonomy.normalizeStyle("moderno")).contains("modern");
        assertThat(ProductTaxonomy.normalizeStyle("simple")).contains("minimal");
        assertThat(ProductTaxonomy.normalizeStyle("toplo")).contains("cozy");
        assertThat(ProductTaxonomy.normalizeStyle("klasično")).contains("classic");
        assertThat(ProductTaxonomy.normalizeStyle("industrijski")).contains("industrial");
        assertThat(ProductTaxonomy.normalizeStyle("natural")).contains("boho");
    }

    @Test
    void plannerRejectsProductsWithoutPositivePrice() {
        Product good = product("good", "in-stock", true, 49.99);
        Product free = product("free", "in-stock", true, 0.0);
        Product missingPrice = product("missing-price", "in-stock", true, 19.99);
        missingPrice.setPrice(null);

        assertThat(ProductTaxonomy.canEnterPlanner(good)).isTrue();
        assertThat(ProductTaxonomy.canEnterPlanner(free)).isFalse();
        assertThat(ProductTaxonomy.canEnterPlanner(missingPrice)).isFalse();
    }

    private Product product(String id, String availability, boolean inStock, double price) {
        Product product = new Product();
        product.setId(id);
        product.setName(id);
        product.setRetailer("IKEA");
        product.setCategory("decor");
        product.setPrice(java.math.BigDecimal.valueOf(price));
        product.setRoomTags("living-room");
        product.setStyleTags("modern");
        product.setAvailabilityStatus(availability);
        product.setInStock(inStock);
        return product;
    }

}
