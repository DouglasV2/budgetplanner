package ai.budgetspace.planner;

import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.dto.PlanItemDto;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.ProductDto;
import ai.budgetspace.dto.ReplaceProductRequest;
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
                List.of("IKEA", "JYSK")
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
                List.of("IKEA")
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
                List.of()
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
        return product;
    }
}
