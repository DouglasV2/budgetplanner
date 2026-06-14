package ai.budgetspace.product;

import ai.budgetspace.dto.CatalogHealthDto;
import ai.budgetspace.dto.RoomReadinessDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogHealthServiceTest {

    @Test
    void livingRoomIsReadyWhenSofaAndTvUnitAreUsable() {
        CatalogHealthService service = service(List.of(
                product("p1", "sofa", "living-room", "in-stock", "complete"),
                product("p2", "tv-unit", "living-room", "in-stock", "complete"),
                product("p3", "rug", "living-room", "in-stock", "partial")
        ));

        CatalogHealthDto health = service.compute();

        assertThat(health.usableProducts()).isEqualTo(3);
        assertThat(livingRoom(health).ready()).isTrue();
        assertThat(livingRoom(health).missingRequiredCategories()).isEmpty();
    }

    @Test
    void livingRoomIsNotReadyWhenSofaIsMissing() {
        CatalogHealthService service = service(List.of(
                product("p1", "tv-unit", "living-room", "in-stock", "complete"),
                product("p2", "rug", "living-room", "in-stock", "complete")
        ));

        CatalogHealthDto health = service.compute();

        assertThat(livingRoom(health).ready()).isFalse();
        assertThat(livingRoom(health).missingRequiredCategories()).contains("sofa");
    }

    @Test
    void needsReviewAndUnavailableAreNotCountedAsUsable() {
        CatalogHealthService service = service(List.of(
                product("good", "sofa", "living-room", "in-stock", "complete"),
                product("review", "sofa", "living-room", "in-stock", "needs-review"),
                product("gone", "sofa", "living-room", "unavailable", "complete")
        ));

        CatalogHealthDto health = service.compute();

        assertThat(health.usableProducts()).isEqualTo(1);
        assertThat(health.blockedProducts()).isEqualTo(2);
        assertThat(health.needsReviewProducts()).isEqualTo(1);
        assertThat(health.unavailableProducts()).isEqualTo(1);
    }

    private CatalogHealthService service(List<Product> products) {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(products);
        return new CatalogHealthService(repository);
    }

    private RoomReadinessDto livingRoom(CatalogHealthDto health) {
        return health.plannerReadiness().stream()
                .filter(readiness -> readiness.room().equals("living-room"))
                .findFirst()
                .orElseThrow();
    }

    private Product product(String id, String category, String room, String availability, String dataQuality) {
        Product product = new Product();
        product.setId(id);
        product.setExternalId(id);
        product.setName("Proizvod " + id);
        product.setRetailer("IKEA");
        product.setCategory(category);
        product.setPrice(BigDecimal.valueOf(100));
        product.setStyleTags("modern");
        product.setRoomTags(room);
        product.setImageUrl("https://example.com/i.jpg");
        product.setProductUrl("https://example.com/p");
        product.setImage("https://example.com/i.jpg");
        product.setUrl("https://example.com/p");
        product.setAvailabilityStatus(availability);
        product.setInStock(!"unavailable".equalsIgnoreCase(availability));
        product.setLastCheckedAt(LocalDate.now().toString());
        product.setPriceTier("standard");
        product.setDataQuality(dataQuality);
        product.setNote("");
        return product;
    }
}
