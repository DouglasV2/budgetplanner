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
 * Sprint 10.35 — France (FR), the first IKEA France catalog. Ported from the IKEA Italy set via the
 * global article-number trick to {@code /fr/fr/}; each row's French name (og:title) + per-market EUR
 * price (JSON-LD) + verified og:image were read off ikea.com/fr on 2026-06-18. Proves the file imports
 * cleanly, every row is market=FR / IKEA / planner-eligible with a verified image, and the core rooms are
 * covered — and that the planner can build a real (non-partial) FR plan.
 */
class IkeaFranceCatalogRuntimeTest {

    @Test
    void franceCatalogImportsCleanlyWithVerifiedImagesAcrossRooms() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = load("/catalog/real-ikea-fr-rooms.json");
        assertThat(snapshot).as("IKEA FR rows").hasSizeGreaterThanOrEqualTo(60);

        List<Product> saved = importAll(snapshot);

        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getMarket()).isEqualTo("FR");
            assertThat(product.getRetailer()).isEqualTo("IKEA");
            assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(product.getProductUrl()).startsWith("https://www.ikea.com/fr/");
            assertThat(URI.create(product.getProductUrl()).getHost()).isNotBlank();
            assertThat(product.isImageVerified()).as("imageVerified %s", product.getExternalId()).isTrue();
            assertThat(product.getImageUrl()).as("imageUrl %s", product.getExternalId()).isNotBlank();
            assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                    .as("planner-eligible %s", product.getExternalId()).isTrue();
        });

        // Core rooms covered (the FR catalog spans living-room/bedroom/home-office/kitchen/bathroom/hallway/dining).
        List<String> rooms = List.of("living-room", "bedroom", "home-office", "kitchen", "bathroom", "hallway", "dining-room");
        for (String room : rooms) {
            assertThat(saved).as("FR catalog covers %s", room)
                    .anySatisfy(p -> assertThat(p.getRoomTags()).contains(room));
        }
    }

    @Test
    void plannerBuildsANonPartialFrenchLivingRoom() throws Exception {
        List<Product> catalog = importAll(load("/catalog/real-ikea-fr-rooms.json"));
        ProductRepository repo = mock(ProductRepository.class);
        when(repo.findAll()).thenReturn(catalog);
        PlannerService planner = new PlannerService(repo);

        PlanGenerationResponse plan = planner.generate(new PlannerInputDto(
                "Salon jusqu'à 1500 €, moderne, IKEA.", 1500, "living-room",
                "modern", "Paris", 22, "multi", List.of("IKEA"), "best-value", "comfort",
                List.of(), List.of(), List.of(), List.of(), List.of(), 0).withMarket("FR"));

        assertThat(plan.input().roomType()).isEqualTo("living-room");
        assertThat(plan.plans()).isNotEmpty();
        assertThat(plan.plans().get(0).items()).isNotEmpty();
        assertThat(plan.plans().get(0).items())
                .allSatisfy(item -> assertThat(item.product().retailer()).isEqualTo("IKEA"));
    }

    private List<Product> importAll(List<RetailerProductSnapshotDto> snapshot) {
        List<Product> saved = new ArrayList<>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenAnswer(inv -> saved.stream()
                .filter(p -> p.getExternalId().equals(inv.getArgument(0))).findFirst());
        when(repository.save(any(Product.class))).thenAnswer(inv -> { saved.add(inv.getArgument(0)); return inv.getArgument(0); });
        ImportSummaryDto summary = new RetailerSnapshotImportService(
                new ProductImportService(repository), new RetailerCatalogAdapter()).importSnapshot(snapshot);
        assertThat(summary.errors()).as("rejected rows: %s", summary.errors()).isEmpty();
        assertThat(summary.created()).isEqualTo(snapshot.size());
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
