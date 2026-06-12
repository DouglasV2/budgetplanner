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
}
