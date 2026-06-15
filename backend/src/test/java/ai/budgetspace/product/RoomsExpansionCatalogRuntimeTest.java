package ai.budgetspace.product;

import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.planner.PlannerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.11 — proves the verified IKEA + JYSK HR rooms-expansion snapshot (loaded by
 * {@link RealCatalogSeeder}) gives the planner real, non-partial plans for bedroom, home-office and
 * bathroom (the previously-thin rooms), alongside the new rooms. All rows are real products
 * (name/price/URL verified); no fabrication.
 */
class RoomsExpansionCatalogRuntimeTest {
    private static final List<String> RESOURCES = List.of(
            "/catalog/real-jysk-hr-new-rooms.json",
            "/catalog/real-ikea-jysk-hr-rooms-expansion.json");

    @Test
    void expansionProductsAreRealIkeaOrJyskWithRealUrls() throws Exception {
        List<Product> catalog = importedCatalog();

        assertThat(catalog).isNotEmpty();
        assertThat(catalog).allSatisfy(product -> {
            assertThat(product.getRetailer()).isIn("IKEA", "JYSK");
            assertThat(product.getPrice().signum()).isPositive();
            String url = product.getProductUrl();
            String host = URI.create(url).getHost();
            assertThat(host).as("real product host for %s", product.getExternalId())
                    .matches(".*(ikea\\.com|jysk\\.hr)$");
            assertThat(URI.create(url).getPath().length()).isGreaterThan(1);
        });
    }

    @Test
    void bedroomHomeOfficeAndBathroomPlansAreNonPartial() throws Exception {
        PlannerService planner = plannerWith(importedCatalog());

        PlanGenerationResponse bedroom = planner.generate(input("bedroom", 1500));
        assertThat(bedroom.partialPlan()).as("bedroom partial").isFalse();
        assertThat(categories(bedroom)).contains("bed", "mattress");

        PlanGenerationResponse office = planner.generate(input("home-office", 1000));
        assertThat(office.partialPlan()).as("home-office partial").isFalse();
        assertThat(categories(office)).contains("desk", "chair");

        PlanGenerationResponse bathroom = planner.generate(input("bathroom", 500));
        assertThat(bathroom.partialPlan()).as("bathroom partial").isFalse();
        assertThat(categories(bathroom)).contains("storage");
    }

    @Test
    void bedroomCanIncludeNightstandWardrobeAndDresser() throws Exception {
        PlannerService planner = plannerWith(importedCatalog());

        // "complete" plan (index 2) pulls in comfort/later categories when budget allows.
        PlanGenerationResponse bedroom = planner.generate(input("bedroom", 1200, "complete"));
        List<String> cats = bedroom.plans().get(2).items().stream()
                .map(item -> item.product().category()).toList();
        assertThat(cats).contains("nightstand");
        assertThat(cats).containsAnyOf("wardrobe", "dresser");
    }

    private List<Product> importedCatalog() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = load();
        List<Product> saved = new ArrayList<>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenAnswer(invocation -> saved.stream()
                .filter(product -> product.getExternalId().equals(invocation.getArgument(0)))
                .findFirst());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            saved.add(product);
            return product;
        });
        new RetailerSnapshotImportService(new ProductImportService(repository), new RetailerCatalogAdapter())
                .importSnapshot(snapshot);
        return saved;
    }

    private PlannerService plannerWith(List<Product> products) {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(products);
        return new PlannerService(repository);
    }

    private PlannerInputDto input(String roomType, int budget) {
        return input(roomType, budget, "comfort");
    }

    private PlannerInputDto input(String roomType, int budget, String level) {
        return new PlannerInputDto("", budget, roomType, "modern", "Zagreb", 20, "multi",
                List.of("IKEA", "JYSK"), "best-value", level,
                List.of(), List.of(), List.of(), List.of(), List.of(), 0);
    }

    private List<String> categories(PlanGenerationResponse response) {
        return response.plans().get(0).items().stream().map(item -> item.product().category()).toList();
    }

    private List<RetailerProductSnapshotDto> load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<RetailerProductSnapshotDto> all = new ArrayList<>();
        for (String resource : RESOURCES) {
            try (InputStream in = getClass().getResourceAsStream(resource)) {
                assertThat(in).as("catalog resource %s", resource).isNotNull();
                all.addAll(mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {}));
            }
        }
        return all;
    }
}
