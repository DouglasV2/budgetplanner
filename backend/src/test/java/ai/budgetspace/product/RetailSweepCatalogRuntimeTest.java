package ai.budgetspace.product;

import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.48 — retail re-sweep. A second wave of verified static-priced retailers across ten markets
 * (HR/SI/IT/AT/FI/FR/PT/ES/NL/SK), sourced deterministically (JSON-LD / PrestaShop itemprop / Shopify /
 * visible €). Proves every {@code real-<market>-retailers-2.json} imports cleanly, each row is the right
 * market / a registered MANUAL_VERIFIED_ONLY retailer / planner-eligible with the honest current price only
 * (no fabricated discount), no replacement chars in the name, and an image only when verified; and that the
 * 13 new retailers (+ Conforama, flipped from feed-required for conforama.it) are all present.
 */
class RetailSweepCatalogRuntimeTest {

    private static final List<String> FILES = List.of(
            "/catalog/real-hr-retailers-2.json", "/catalog/real-si-retailers-2.json",
            "/catalog/real-it-retailers-2.json", "/catalog/real-at-retailers-2.json",
            "/catalog/real-fi-retailers-2.json", "/catalog/real-fr-retailers-2.json",
            "/catalog/real-pt-retailers-2.json", "/catalog/real-es-retailers-2.json",
            "/catalog/real-nl-retailers-2.json", "/catalog/real-sk-retailers-2.json");

    private static final Set<String> NEW_RETAILERS = Set.of(
            "Svijetnamještaja", "Svetpohištva", "Conforama", "Interio", "Masku", "Lovely Meubles", "JOM",
            "Sítio do Móvel", "Miroytengo", "Merkamueble", "Muebles BOOM", "Pronto Wonen", "Drevona", "ASKO Nábytok");

    private static final Set<String> MARKETS = Set.of("HR", "SI", "IT", "AT", "FI", "FR", "PT", "ES", "NL", "SK");

    @Test
    void retailSweepCatalogsImportCleanlyAndAreRegistered() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = new ArrayList<>();
        for (String file : FILES) snapshot.addAll(load(file));
        assertThat(snapshot).as("retail-sweep rows").hasSizeGreaterThanOrEqualTo(150);

        List<Product> saved = new ArrayList<>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenAnswer(inv -> saved.stream()
                .filter(p -> p.getExternalId().equals(inv.getArgument(0))).findFirst());
        when(repository.save(any(Product.class))).thenAnswer(inv -> { saved.add(inv.getArgument(0)); return inv.getArgument(0); });

        ImportSummaryDto summary = new RetailerSnapshotImportService(
                new ProductImportService(repository), new RetailerCatalogAdapter()).importSnapshot(snapshot);
        assertThat(summary.errors()).as("rejected rows: %s", summary.errors()).isEmpty();
        assertThat(summary.created()).isEqualTo(snapshot.size());

        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getMarket()).as("market %s", product.getExternalId()).isIn(MARKETS);
            assertThat(product.getRetailer()).as("retailer %s", product.getExternalId()).isIn(NEW_RETAILERS);
            assertThat(product.getName()).as("no replacement char %s", product.getExternalId()).doesNotContain("�");
            assertThat(product.getPrice().doubleValue()).as("price>=25 %s", product.getExternalId()).isGreaterThanOrEqualTo(25);
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(product.getProductUrl()).startsWith("https://");
            assertThat(URI.create(product.getProductUrl()).getHost()).isNotBlank();
            assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                    .as("planner-eligible %s", product.getExternalId()).isTrue();
            assertThat(product.getOriginalPrice()).as("no fabricated discount %s", product.getExternalId()).isNull();
            if (product.isImageVerified()) {
                assertThat(product.getImageUrl()).as("imageUrl %s", product.getExternalId()).isNotBlank();
            }
        });

        // Every new retailer is present and policy-registered as verified (not feed-required).
        Set<String> present = saved.stream().map(Product::getRetailer).collect(Collectors.toSet());
        assertThat(present).as("all new retailers have products").containsAll(NEW_RETAILERS);
        for (String retailer : NEW_RETAILERS) {
            assertThat(CatalogSourcePolicy.statusFor(retailer)).as("%s status", retailer)
                    .isEqualTo(CatalogSourcePolicy.SourcingStatus.MANUAL_VERIFIED_ONLY);
            assertThat(CatalogSourcePolicy.isFeedRequired(retailer)).as("%s feed-required", retailer).isFalse();
        }
        // Conforama was feed-required (FR anti-bot) → now verified because conforama.it serves static prices.
        assertThat(CatalogSourcePolicy.isFeedRequired("Conforama")).isFalse();
    }

    private List<RetailerProductSnapshotDto> load(String resource) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertThat(in).as("catalog resource %s", resource).isNotNull();
            return mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }
}
