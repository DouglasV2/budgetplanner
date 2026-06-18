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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.39 — Spain (ES), the 10th market: IKEA-only (no JYSK in Spain), like FR/IT. Ported from the
 * IKEA Italy set via the article-number trick to {@code /es/es/}; each row's Spanish name (og:title) +
 * per-market EUR price (JSON-LD) + verified og:image read off ikea.com/es on 2026-06-18. Proves the file
 * imports cleanly, every row is market=ES / IKEA / planner-eligible with a verified image, the core rooms
 * are covered, and the planner builds a real (non-partial) ES plan.
 */
class SpainCatalogRuntimeTest {

    @Test
    void spainCatalogImportsCleanlyWithVerifiedImagesAcrossRooms() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = load("/catalog/real-ikea-es-rooms.json");
        assertThat(snapshot).as("IKEA ES rows").hasSizeGreaterThanOrEqualTo(55);

        List<Product> saved = importAll(snapshot);

        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getMarket()).isEqualTo("ES");
            assertThat(product.getRetailer()).isEqualTo("IKEA");
            assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(product.getProductUrl()).startsWith("https://www.ikea.com/es/");
            assertThat(URI.create(product.getProductUrl()).getHost()).isNotBlank();
            assertThat(product.isImageVerified()).as("imageVerified %s", product.getExternalId()).isTrue();
            assertThat(product.getImageUrl()).as("imageUrl %s", product.getExternalId()).isNotBlank();
            assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                    .as("planner-eligible %s", product.getExternalId()).isTrue();
        });

        for (String room : List.of("living-room", "bedroom", "home-office", "dining-room")) {
            assertThat(saved).as("ES catalog covers %s", room)
                    .anySatisfy(p -> assertThat(p.getRoomTags()).contains(room));
        }
    }

    @Test
    void plannerBuildsANonPartialSpanishLivingRoom() throws Exception {
        List<Product> catalog = importAll(load("/catalog/real-ikea-es-rooms.json"));
        ProductRepository repo = mock(ProductRepository.class);
        when(repo.findAll()).thenReturn(catalog);
        PlannerService planner = new PlannerService(repo);

        PlanGenerationResponse plan = planner.generate(new PlannerInputDto(
                "Salón hasta 1500 €, moderno, IKEA.", 1500, "living-room",
                "modern", "Madrid", 24, "multi", List.of("IKEA"), "best-value", "comfort",
                List.of(), List.of(), List.of(), List.of(), List.of(), 0).withMarket("ES"));

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
