package ai.budgetspace.product;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTaxonomyTest {
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
