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
 * Sprint 10.55 — United Kingdom (GB), the 15th market: IKEA-only (no JYSK in the UK), GBP. The IKEA GB set was
 * ported via the article-number trick to {@code /gb/en/}; each row's English name + GBP price + verified
 * og:image was read off ikea.com/gb/en on 2026-06-19 (the deterministic number-trick URLs were verified to
 * resolve to a live product; the few combination-article URLs were spot-checked LIVE). Proves the file imports
 * cleanly, every row is market=GB / IKEA / planner-eligible with a verified image and a www.ikea.com/gb URL,
 * externalIds + URLs are unique (the catalog was deduped), every room + core anchor category is covered, and the
 * planner builds a real (non-partial) GB plan.
 */
class GbCatalogRuntimeTest {

    @Test
    void gbCatalogImportsCleanlyWithVerifiedImagesAcrossRooms() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = load("/catalog/real-ikea-gb-rooms.json");
        assertThat(snapshot).as("IKEA GB rows").hasSizeGreaterThanOrEqualTo(45);

        List<Product> saved = importAll(snapshot);

        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getMarket()).isEqualTo("GB");
            assertThat(product.getRetailer()).isEqualTo("IKEA");
            assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(product.getProductUrl()).startsWith("https://www.ikea.com/gb/");
            assertThat(URI.create(product.getProductUrl()).getHost()).isEqualTo("www.ikea.com");
            assertThat(product.isImageVerified()).as("imageVerified %s", product.getExternalId()).isTrue();
            assertThat(product.getImageUrl()).as("imageUrl %s", product.getExternalId()).isNotBlank();
            assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                    .as("planner-eligible %s", product.getExternalId()).isTrue();
        });

        // The catalog was deduped — guard against a regression reintroducing duplicate ids/links.
        assertThat(saved).extracting(Product::getExternalId).doesNotHaveDuplicates();
        assertThat(saved).extracting(Product::getProductUrl).doesNotHaveDuplicates();

        // Every planner room is covered.
        for (String room : List.of("living-room", "bedroom", "home-office", "dining-room", "kitchen", "hallway", "bathroom")) {
            assertThat(saved).as("GB catalog covers %s", room)
                    .anySatisfy(p -> assertThat(p.getRoomTags()).contains(room));
        }
        // Core anchor categories present, so no core planner cell is empty.
        for (String category : List.of("sofa", "tv-unit", "bed", "mattress", "desk", "chair", "dining-table", "dining-chair")) {
            assertThat(saved).as("GB catalog has a %s", category)
                    .anySatisfy(p -> assertThat(p.getCategory()).isEqualTo(category));
        }
    }

    @Test
    void plannerBuildsANonPartialUkLivingRoom() throws Exception {
        List<Product> catalog = importAll(load("/catalog/real-ikea-gb-rooms.json"));
        ProductRepository repo = mock(ProductRepository.class);
        when(repo.findAll()).thenReturn(catalog);
        PlannerService planner = new PlannerService(repo);

        PlanGenerationResponse plan = planner.generate(new PlannerInputDto(
                "Living room up to £1500, modern, IKEA.", 1500, "living-room",
                "modern", "London", 24, "multi", List.of("IKEA"), "best-value", "comfort",
                List.of(), List.of(), List.of(), List.of(), List.of(), 0).withMarket("GB"));

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
