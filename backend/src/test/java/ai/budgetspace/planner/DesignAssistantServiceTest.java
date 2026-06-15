package ai.budgetspace.planner;

import ai.budgetspace.dto.DesignAssistantResponse;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DesignAssistantServiceTest {
    private final DesignAssistantService service = new DesignAssistantService();

    @Test
    void describesGeneratedPlanWithRoomBudgetAndCategories() {
        PlannerService planner = plannerWith(List.of(
                product("sofa-1", "Kauč svijetli", "IKEA", "sofa", 650),
                product("tv-1", "TV komoda", "IKEA", "tv-unit", 180)
        ));
        PlanGenerationResponse plan = planner.generate(input("Imam 1500 € za dnevni boravak."));

        DesignAssistantResponse response = service.describe(plan);

        assertThat(response.summary()).contains("dnevni boravak");
        assertThat(response.summary()).contains("1500");
        assertThat(response.summary()).contains("kauč");
        assertThat(response.highlights()).isNotEmpty();
    }

    @Test
    void handlesEmptyPlanGracefully() {
        PlanGenerationResponse empty = new PlanGenerationResponse(
                input("Imam 1500 € za dnevni boravak.").normalized(), List.of(), false, List.of(), null);

        DesignAssistantResponse response = service.describe(empty);

        assertThat(response.summary()).isNotBlank();
        assertThat(response.highlights()).isEmpty();
    }

    @Test
    void handlesNullPlanGracefully() {
        DesignAssistantResponse response = service.describe(null);

        assertThat(response.summary()).isNotBlank();
    }

    private PlannerService plannerWith(List<Product> products) {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(products);
        return new PlannerService(repository);
    }

    private PlannerInputDto input(String prompt) {
        return new PlannerInputDto(
                prompt, 1500, "living-room", "modern", "Zagreb", 20, "multi",
                List.of("IKEA", "JYSK"), "best-value", "comfort",
                List.of(), List.of(), List.of(), List.of(), List.of(), 0
        );
    }

    private Product product(String id, String name, String retailer, String category, int price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setRetailer(retailer);
        product.setCategory(category);
        product.setPrice(BigDecimal.valueOf(price));
        product.setStyleTags("modern,scandinavian");
        product.setRoomTags("living-room");
        product.setImage("https://example.com/image.jpg");
        product.setUrl("https://example.com/product");
        product.setImageUrl("https://example.com/image.jpg");
        product.setProductUrl("https://example.com/product");
        product.setAvailabilityStatus("in-stock");
        product.setDeliveryNote("Provjeri dostavu ili preuzimanje prije kupnje.");
        product.setLastCheckedAt("2026-06-12");
        product.setExternalId(id);
        product.setPriceTier(price <= 120 ? "budget" : price >= 450 ? "premium" : "standard");
        product.setRating(4.5);
        product.setInStock(true);
        product.setNote("Dobar omjer cijene i korisnosti.");
        return product;
    }
}
