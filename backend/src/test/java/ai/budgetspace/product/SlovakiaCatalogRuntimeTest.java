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
 * Sprint 10.38 — Slovakia (SK), the 9th market: a two-retailer EUR market (IKEA + JYSK). IKEA SK ported
 * from the IKEA IT set via the article-number trick to {@code /sk/sk/}; JYSK SK sourced from jysk.sk (same
 * static price structure as jysk.nl/hr). Proves both files import cleanly, every row is market=SK /
 * IKEA-or-JYSK / planner-eligible with a verified image, both retailers + core rooms are present, and the
 * planner builds a real (non-partial) SK plan. (Market code SK / language sk — distinct from SI / sl.)
 */
class SlovakiaCatalogRuntimeTest {

    @Test
    void slovakiaCatalogImportsCleanlyWithBothRetailersAndVerifiedImages() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = new ArrayList<>();
        snapshot.addAll(load("/catalog/real-ikea-sk-rooms.json"));
        snapshot.addAll(load("/catalog/real-jysk-sk-rooms.json"));
        assertThat(snapshot).as("SK rows (IKEA + JYSK)").hasSizeGreaterThanOrEqualTo(90);

        List<Product> saved = importAll(snapshot);

        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getMarket()).isEqualTo("SK");
            assertThat(product.getRetailer()).isIn("IKEA", "JYSK");
            assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(product.getProductUrl()).startsWith("https://");
            assertThat(URI.create(product.getProductUrl()).getHost()).isNotBlank();
            assertThat(product.isImageVerified()).as("imageVerified %s", product.getExternalId()).isTrue();
            assertThat(product.getImageUrl()).as("imageUrl %s", product.getExternalId()).isNotBlank();
            assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                    .as("planner-eligible %s", product.getExternalId()).isTrue();
        });

        assertThat(saved).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("IKEA"));
        assertThat(saved).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("JYSK"));

        for (String room : List.of("living-room", "bedroom", "home-office", "dining-room")) {
            assertThat(saved).as("SK covers %s", room)
                    .anySatisfy(p -> assertThat(p.getRoomTags()).contains(room));
        }
    }

    @Test
    void plannerBuildsANonPartialSlovakLivingRoom() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = new ArrayList<>();
        snapshot.addAll(load("/catalog/real-ikea-sk-rooms.json"));
        snapshot.addAll(load("/catalog/real-jysk-sk-rooms.json"));
        List<Product> catalog = importAll(snapshot);
        ProductRepository repo = mock(ProductRepository.class);
        when(repo.findAll()).thenReturn(catalog);
        PlannerService planner = new PlannerService(repo);

        PlanGenerationResponse plan = planner.generate(new PlannerInputDto(
                "Obývačka do 1500 €, moderná, IKEA a JYSK.", 1500, "living-room",
                "modern", "Bratislava", 24, "multi", List.of("IKEA", "JYSK"), "best-value", "comfort",
                List.of(), List.of(), List.of(), List.of(), List.of(), 0).withMarket("SK"));

        assertThat(plan.input().roomType()).isEqualTo("living-room");
        assertThat(plan.plans()).isNotEmpty();
        assertThat(plan.plans().get(0).items()).isNotEmpty();
        assertThat(plan.plans().get(0).items())
                .allSatisfy(item -> assertThat(item.product().retailer()).isIn("IKEA", "JYSK"));
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
