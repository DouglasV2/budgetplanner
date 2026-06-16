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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.19 — JYSK SI/DE hallway + kitchen depth (those markets previously had JYSK only for
 * living-room/bedroom/dining/office). Proves the two market files import cleanly through the validated
 * pipeline (zero rejected rows, real jysk.* product URLs, {@code public-product-page} provenance) and
 * that the hallway storage + kitchen coverage exists. Every row was web-verified on its live single
 * product page on 2026-06-16. JYSK AT was intentionally excluded this sprint: jysk.at gates per-product
 * stock behind JavaScript (static HTML shows "Vorübergehend ausverkauft"), so availability could not be
 * honestly confirmed without a feed/API — coverage was not forced.
 */
class JyskEuRoomsCatalogRuntimeTest {

    private static final List<String> RESOURCES = List.of(
            "/catalog/real-jysk-si-rooms.json",
            "/catalog/real-jysk-de-rooms.json");

    @Test
    void jyskEuRoomsCatalogsImportCleanly() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = loadAll();
        assertThat(snapshot).as("total JYSK SI/DE room rows").hasSizeGreaterThanOrEqualTo(40);

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
            assertThat(product.getRetailer()).isEqualTo("JYSK");
            assertThat(product.getMarket()).isIn("SI", "DE");
            assertThat(product.getPrice().signum()).as("price > 0 for %s", product.getExternalId()).isPositive();
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(product.getProductUrl()).as("real URL for %s", product.getExternalId()).startsWith("https://jysk.");
            assertThat(URI.create(product.getProductUrl()).getHost()).as("host for %s", product.getExternalId()).isNotBlank();
            assertThat(ProductTaxonomy.canEnterPlanner(product)).as("usable %s", product.getExternalId()).isTrue();
        });

        // Both markets contribute hallway storage (the core hallway category).
        for (String market : List.of("SI", "DE")) {
            assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo(market); assertThat(p.getRoomTags()).contains("hallway"); assertThat(p.getCategory()).isEqualTo("storage"); });
            assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo(market); assertThat(p.getRoomTags()).contains("kitchen"); });
        }
        // A kitchen cart and a hallway rug exist somewhere in the JYSK EU set.
        assertThat(saved).anySatisfy(p -> assertThat(p.getCategory()).isEqualTo("kitchen-cart"));
        assertThat(saved).anySatisfy(p -> { assertThat(p.getCategory()).isEqualTo("rug"); assertThat(p.getRoomTags()).contains("hallway"); });
    }

    private List<RetailerProductSnapshotDto> loadAll() throws Exception {
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
