package ai.budgetspace.product;

import ai.budgetspace.dto.ImportSummaryDto;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.26 — HR catalog breadth. More options per anchor category from the reachable HR retailers
 * (IKEA HR beds/mattresses/wardrobes/nightstands/dressers — previously ABSENT — plus more desks, office
 * chairs, sofas, coffee tables, TV units; JYSK + Emmezeta beds/wardrobes/dining/dressers). Proves the file
 * imports cleanly, every row is planner-eligible AND carries a verified image, and the IKEA bedroom gap is
 * now covered. Each row was web-verified (name + EUR price; og:image identity-checked and confirmed to load).
 */
class HrBreadthCatalogRuntimeTest {

    @Test
    void hrBreadthCatalogImportsCleanlyWithVerifiedImagesAndFillsAnchorGaps() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = load("/catalog/real-hr-breadth-10-26.json");
        assertThat(snapshot).as("HR-breadth rows").hasSizeGreaterThanOrEqualTo(30);

        List<Product> saved = new ArrayList<>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            saved.add(product);
            return product;
        });

        ImportSummaryDto summary = new RetailerSnapshotImportService(
                new ProductImportService(repository), new RetailerCatalogAdapter()).importSnapshot(snapshot);

        assertThat(summary.errors()).as("rejected rows: %s", summary.errors()).isEmpty();
        assertThat(summary.created()).isEqualTo(snapshot.size());

        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getMarket()).isEqualTo("HR");
            assertThat(product.getRetailer()).isIn("IKEA", "JYSK", "Emmezeta");
            assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(product.getProductUrl()).startsWith("https://");
            assertThat(URI.create(product.getProductUrl()).getHost()).isNotBlank();
            // Every breadth row ships with a verified product image (that's how it was sourced).
            assertThat(product.isImageVerified()).as("imageVerified %s", product.getExternalId()).isTrue();
            assertThat(product.getImageUrl()).as("imageUrl %s", product.getExternalId()).isNotBlank();
            // Verified-only gate: every breadth row reaches the planner.
            assertThat(CatalogSourcePolicy.isPlannerEligible(product)).as("planner-eligible %s", product.getExternalId()).isTrue();
        });

        // The IKEA HR bedroom gap (previously zero beds/mattresses/wardrobes) is now covered.
        assertThat(saved).anySatisfy(p -> { assertThat(p.getRetailer()).isEqualTo("IKEA"); assertThat(p.getCategory()).isEqualTo("bed"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getRetailer()).isEqualTo("IKEA"); assertThat(p.getCategory()).isEqualTo("mattress"); });
        assertThat(saved).anySatisfy(p -> { assertThat(p.getRetailer()).isEqualTo("IKEA"); assertThat(p.getCategory()).isEqualTo("wardrobe"); });
    }

    /**
     * The point of breadth (owner's concern: "with lots of furniture, does the planner pick the right one?").
     * IKEA HR previously had no bedroom anchors, so an IKEA-preferring bedroom prompt couldn't pick IKEA.
     * With the breadth catalog the planner's scoring (style/room/price/retailer) now builds a real,
     * non-partial IKEA bedroom — proving the new rows are reachable AND selected, not just present.
     */
    @Test
    void plannerBuildsANonPartialIkeaBedroomFromTheBreadthCatalog() throws Exception {
        List<Product> catalog = importedCatalog("/catalog/real-hr-breadth-10-26.json");
        ProductRepository repo = mock(ProductRepository.class);
        when(repo.findAll()).thenReturn(catalog);
        PlannerService planner = new PlannerService(repo);

        PlanGenerationResponse plan = planner.generate(new PlannerInputDto(
                "Spavaća soba do 1500 €, moderno, najviše IKEA, trebam krevet i madrac.", 1500, "bedroom",
                "modern", "Zagreb", 20, "multi", List.of("IKEA"), "best-value", "comfort",
                List.of(), List.of(), List.of(), List.of(), List.of(), 0));

        assertThat(plan.input().roomType()).isEqualTo("bedroom");
        assertThat(plan.partialPlan()).as("bedroom plan should be complete now IKEA has beds+mattresses").isFalse();
        List<String> categories = plan.plans().get(0).items().stream().map(i -> i.product().category()).toList();
        assertThat(categories).contains("bed", "mattress");
        // The IKEA preference is honoured: at least one IKEA item, and IKEA beds are now reachable.
        assertThat(plan.plans().get(0).items()).anySatisfy(i -> assertThat(i.product().retailer()).isEqualTo("IKEA"));
        assertThat(catalog).anySatisfy(p -> { assertThat(p.getRetailer()).isEqualTo("IKEA"); assertThat(p.getCategory()).isEqualTo("bed"); });
    }

    private List<Product> importedCatalog(String resource) throws Exception {
        List<Product> saved = new ArrayList<>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenAnswer(inv -> saved.stream()
                .filter(p -> p.getExternalId().equals(inv.getArgument(0))).findFirst());
        when(repository.save(any(Product.class))).thenAnswer(inv -> { saved.add(inv.getArgument(0)); return inv.getArgument(0); });
        new RetailerSnapshotImportService(new ProductImportService(repository), new RetailerCatalogAdapter())
                .importSnapshot(load(resource));
        return saved;
    }

    private List<RetailerProductSnapshotDto> load(String resource) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertThat(in).as("catalog resource %s", resource).isNotNull();
            return mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }
}
