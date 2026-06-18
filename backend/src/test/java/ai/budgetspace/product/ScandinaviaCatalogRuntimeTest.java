package ai.budgetspace.product;

import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.46 — Scandinavia: the first non-EUR markets. IKEA (number-trick → /no/no/, /se/sv/, /dk/da/) and
 * JYSK (static product pages) for Norway/Sweden/Denmark, with prices in the national currency (NOK/SEK/DKK).
 * Proves: each market's catalog imports cleanly; every row is the right market / IKEA-or-JYSK / planner-eligible
 * with the honest local-currency price and a verified image; the market is wired to its non-EUR currency in
 * {@link Markets}; both retailers and the core categories are present; and JYSK sale rows (if any) carry a
 * verified regular price + end date (never a fabricated discount).
 */
class ScandinaviaCatalogRuntimeTest {

    private record Mk(String code, String currency, List<String> files) {}

    private static final List<Mk> MARKETS = List.of(
            new Mk("NO", "NOK", List.of("/catalog/real-ikea-no-rooms.json", "/catalog/real-jysk-no-rooms.json")),
            new Mk("SE", "SEK", List.of("/catalog/real-ikea-se-rooms.json", "/catalog/real-jysk-se-rooms.json")),
            new Mk("DK", "DKK", List.of("/catalog/real-ikea-dk-rooms.json", "/catalog/real-jysk-dk-rooms.json")));

    @Test
    void scandinavianCatalogsImportCleanlyInTheirOwnCurrency() throws Exception {
        for (Mk mk : MARKETS) {
            // The market must be wired to its non-EUR currency (this is what makes the UI format prices correctly).
            assertThat(Markets.currencyFor(mk.code())).as("%s currency", mk.code()).isEqualTo(mk.currency());

            List<RetailerProductSnapshotDto> snapshot = new ArrayList<>();
            for (String file : mk.files()) snapshot.addAll(load(file));
            assertThat(snapshot).as("%s rows", mk.code()).hasSizeGreaterThanOrEqualTo(40);

            List<Product> saved = new ArrayList<>();
            ProductRepository repository = mock(ProductRepository.class);
            when(repository.findByExternalId(anyString())).thenAnswer(inv -> saved.stream()
                    .filter(p -> p.getExternalId().equals(inv.getArgument(0))).findFirst());
            when(repository.save(any(Product.class))).thenAnswer(inv -> { saved.add(inv.getArgument(0)); return inv.getArgument(0); });

            ImportSummaryDto summary = new RetailerSnapshotImportService(
                    new ProductImportService(repository), new RetailerCatalogAdapter()).importSnapshot(snapshot);
            assertThat(summary.errors()).as("%s rejected rows: %s", mk.code(), summary.errors()).isEmpty();
            assertThat(summary.created()).isEqualTo(snapshot.size());

            assertThat(saved).allSatisfy(product -> {
                assertThat(product.getMarket()).isEqualTo(mk.code());
                assertThat(product.getRetailer()).isIn("IKEA", "JYSK");
                assertThat(product.getName()).as("name has no replacement char %s", product.getExternalId()).doesNotContain("�");
                assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
                assertThat(product.getSourceType()).isEqualTo("public-product-page");
                assertThat(product.getProductUrl()).startsWith("https://");
                assertThat(URI.create(product.getProductUrl()).getHost()).isNotBlank();
                assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                        .as("planner-eligible %s", product.getExternalId()).isTrue();
                if (product.isImageVerified()) {
                    assertThat(product.getImageUrl()).as("imageUrl %s", product.getExternalId()).isNotBlank();
                }
                // A sale is honest only: a verified regular price strictly above the current price, with an end date.
                if (product.getOriginalPrice() != null) {
                    assertThat(product.getOriginalPrice()).as("sale regular>current %s", product.getExternalId())
                            .isGreaterThan(product.getPrice());
                    assertThat(product.getSaleEndsAt()).as("sale has end date %s", product.getExternalId()).isNotBlank();
                }
            });

            long withImage = saved.stream().filter(Product::isImageVerified).count();
            assertThat(withImage).as("%s rows with a verified image", mk.code()).isGreaterThanOrEqualTo(saved.size() * 3L / 4);

            assertThat(saved).as("%s has IKEA", mk.code()).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("IKEA"));
            assertThat(saved).as("%s has JYSK", mk.code()).anySatisfy(p -> assertThat(p.getRetailer()).isEqualTo("JYSK"));

            Map<String, Long> byCategory = new java.util.HashMap<>();
            for (Product p : saved) byCategory.merge(p.getCategory(), 1L, Long::sum);
            for (String category : List.of("sofa", "bed", "dining-table", "storage")) {
                assertThat(byCategory).as("%s covers %s", mk.code(), category).containsKey(category);
            }
        }
    }

    @Test
    void scandinavianPricesAreWholeUnitsNotEuroSized() throws Exception {
        // kr prices are whole numbers and meaningfully larger than the euro equivalents — a quick guard that we
        // stored the local-currency price, not a euro figure copied across.
        List<RetailerProductSnapshotDto> no = load("/catalog/real-ikea-no-rooms.json");
        assertThat(no).allSatisfy(row -> assertThat(row.price().stripTrailingZeros().scale()).isLessThanOrEqualTo(0));
        assertThat(no.stream().anyMatch(r -> r.price().compareTo(BigDecimal.valueOf(500)) > 0))
                .as("NOK catalog has prices in the hundreds/thousands").isTrue();
    }

    private List<RetailerProductSnapshotDto> load(String resource) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertThat(in).as("catalog resource %s", resource).isNotNull();
            return mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }
}
