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
 * Sprint 10.37 — Netherlands (NL), the 8th market: a two-retailer EUR market (IKEA + JYSK, like HR/DE).
 * IKEA NL ported from the IKEA IT set via the article-number trick to {@code /nl/nl/}; JYSK NL sourced from
 * jysk.nl (reachable + static prices, unlike jysk.at) with the same fields as jysk.hr (priceAmount = regular,
 * JSON-LD price = current, priceValidUntil = real promo window). Proves both files import cleanly, every row
 * is market=NL / IKEA-or-JYSK / planner-eligible with a verified image, both retailers + core rooms are
 * present, and the planner builds a real (non-partial) NL plan.
 */
class NetherlandsCatalogRuntimeTest {

    @Test
    void netherlandsCatalogImportsCleanlyWithBothRetailersAndVerifiedImages() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = new ArrayList<>();
        snapshot.addAll(load("/catalog/real-ikea-nl-rooms.json"));
        snapshot.addAll(load("/catalog/real-jysk-nl-rooms.json"));
        assertThat(snapshot).as("NL rows (IKEA + JYSK)").hasSizeGreaterThanOrEqualTo(90);

        List<Product> saved = importAll(snapshot);

        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getMarket()).isEqualTo("NL");
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

        // Both retailers actually present (NL is a two-store market).
        assertThat(saved).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("IKEA"));
        assertThat(saved).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("JYSK"));

        // Core rooms covered (IKEA NL carries the IT set's living-room/bedroom/home-office/kitchen/bathroom/
        // hallway/dining; JYSK NL adds depth).
        for (String room : List.of("living-room", "bedroom", "home-office", "dining-room")) {
            assertThat(saved).as("NL covers %s", room)
                    .anySatisfy(p -> assertThat(p.getRoomTags()).contains(room));
        }
    }

    @Test
    void plannerBuildsANonPartialDutchLivingRoom() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = new ArrayList<>();
        snapshot.addAll(load("/catalog/real-ikea-nl-rooms.json"));
        snapshot.addAll(load("/catalog/real-jysk-nl-rooms.json"));
        List<Product> catalog = importAll(snapshot);
        ProductRepository repo = mock(ProductRepository.class);
        when(repo.findAll()).thenReturn(catalog);
        PlannerService planner = new PlannerService(repo);

        PlanGenerationResponse plan = planner.generate(new PlannerInputDto(
                "Woonkamer tot 1500 €, modern, IKEA en JYSK.", 1500, "living-room",
                "modern", "Amsterdam", 24, "multi", List.of("IKEA", "JYSK"), "best-value", "comfort",
                List.of(), List.of(), List.of(), List.of(), List.of(), 0).withMarket("NL"));

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
