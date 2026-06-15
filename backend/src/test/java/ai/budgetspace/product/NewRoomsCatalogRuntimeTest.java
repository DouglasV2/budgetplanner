package ai.budgetspace.product;

import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.planner.PlannerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.7/10.9 — proves the verified JYSK HR new-rooms snapshot ({@code real-jysk-hr-new-rooms.json},
 * loaded by {@link RealCatalogSeeder} at startup) imports through the runtime pipeline and lets the
 * planner build non-partial plans for dining-room, kitchen and hallway. No live internet: read from
 * the classpath. All rows are real JYSK products (name/price/URL verified on jysk.hr); no fabrication.
 */
class NewRoomsCatalogRuntimeTest {
    private static final String RESOURCE = "/catalog/real-jysk-hr-new-rooms.json";

    @Test
    void newRoomsCatalogImportsRealJyskProductsWithRealUrls() throws Exception {
        List<Product> catalog = importedCatalog();

        assertThat(catalog).isNotEmpty();
        assertThat(catalog).allSatisfy(product -> {
            assertThat(product.getRetailer()).isEqualTo("JYSK");
            assertThat(product.getName()).isNotBlank();
            assertThat(product.getPrice().signum()).isPositive();
            assertThat(product.getProductUrl()).startsWith("https://jysk.hr/");
        });
    }

    @Test
    void diningKitchenAndHallwayPlansAreNonPartial() throws Exception {
        PlannerService planner = plannerWith(importedCatalog());

        PlanGenerationResponse dining = planner.generate(input("Trebam urediti blagovaonicu do 2000 €."));
        assertThat(dining.input().roomType()).isEqualTo("dining-room");
        assertThat(dining.partialPlan()).isFalse();
        assertThat(categories(dining)).contains("dining-table", "dining-chair");

        PlanGenerationResponse kitchen = planner.generate(input("Trebam urediti kuhinju do 1000 €."));
        assertThat(kitchen.input().roomType()).isEqualTo("kitchen");
        assertThat(kitchen.partialPlan()).isFalse();
        assertThat(categories(kitchen)).contains("kitchen-cart");

        PlanGenerationResponse hallway = planner.generate(input("Trebam urediti hodnik do 500 €."));
        assertThat(hallway.input().roomType()).isEqualTo("hallway");
        assertThat(hallway.partialPlan()).isFalse();
        assertThat(categories(hallway)).contains("storage");
    }

    @Test
    void colorAndMaterialTagsArePopulatedFromSnapshot() throws Exception {
        List<Product> catalog = importedCatalog();
        Product kanstrup = catalog.stream()
                .filter(product -> product.getExternalId().equals("jysk-hr-kanstrup-polica-kotaci-crna"))
                .findFirst()
                .orElseThrow();

        assertThat(kanstrup.getColorTags()).contains("black");
        assertThat(kanstrup.getMaterialTags()).contains("metal");
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

    private PlannerInputDto input(String prompt) {
        return new PlannerInputDto(prompt, 1500, "living-room", "modern", "Zagreb", 20, "multi",
                List.of("IKEA", "JYSK"), "best-value", "comfort",
                List.of(), List.of(), List.of(), List.of(), List.of(), 0);
    }

    private List<String> categories(PlanGenerationResponse response) {
        return response.plans().get(0).items().stream().map(item -> item.product().category()).toList();
    }

    private List<RetailerProductSnapshotDto> load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(RESOURCE)) {
            assertThat(in).as("catalog resource %s", RESOURCE).isNotNull();
            return mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }
}
