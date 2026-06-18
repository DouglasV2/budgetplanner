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
 * Sprint 10.36 — France non-IKEA breadth via Camif (camif.fr), the one major French furniture chain that
 * serves its price in static HTML (JSON-LD {@code offers.price} / visible €) so it can be web-verified per
 * product like IKEA/JYSK. Every other big FR chain (Conforama, But, Maisons du Monde, La Redoute, Fly,
 * Habitat, Cdiscount, Vente-unique) is anti-bot (DataDome/Cloudflare 403) → {@code OFFICIAL_FEED_REQUIRED}
 * (never bypassed). Proves the Camif catalog imports cleanly, is market=FR / Camif / planner-eligible with a
 * verified image, and spans the core categories. Camif's standing ~-20% web price is stored as the honest
 * current price (no originalPrice/sale badge — its priceValidUntil is a +1yr schema default, not a real promo).
 */
class CamifFranceCatalogRuntimeTest {

    @Test
    void camifCatalogImportsCleanlyWithVerifiedImagesAcrossCategories() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = load("/catalog/real-camif-fr.json");
        assertThat(snapshot).as("Camif FR rows").hasSizeGreaterThanOrEqualTo(30);

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
            assertThat(product.getMarket()).isEqualTo("FR");
            assertThat(product.getRetailer()).isEqualTo("Camif");
            assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
            assertThat(product.getSourceType()).isEqualTo("public-product-page");
            assertThat(product.getProductUrl()).startsWith("https://");
            assertThat(URI.create(product.getProductUrl()).getHost()).contains("camif.fr");
            assertThat(product.isImageVerified()).as("imageVerified %s", product.getExternalId()).isTrue();
            assertThat(product.getImageUrl()).as("imageUrl %s", product.getExternalId()).isNotBlank();
            assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                    .as("planner-eligible %s", product.getExternalId()).isTrue();
            // Honest current price only — no phantom discount on a standing web price.
            assertThat(product.getOriginalPrice()).as("no originalPrice %s", product.getExternalId()).isNull();
        });

        // Camif is registered as a verified (non-feed) retailer; its big-chain peers are feed-required.
        assertThat(CatalogSourcePolicy.isFeedRequired("Camif")).isFalse();
        // Sprint 10.48: Conforama flipped to verified (conforama.it serves static prices); other FR chains stay feed-required.
        assertThat(CatalogSourcePolicy.isFeedRequired("Maisons du Monde")).isTrue();
        assertThat(CatalogSourcePolicy.isFeedRequired("But")).isTrue();

        // Spans the core furniture categories (sofa/bed/table/storage at minimum).
        for (String category : List.of("sofa", "bed", "table", "storage", "wardrobe")) {
            assertThat(saved).as("Camif covers %s", category)
                    .anySatisfy(p -> assertThat(p.getCategory()).isEqualTo(category));
        }
    }

    private List<RetailerProductSnapshotDto> load(String resource) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertThat(in).as("catalog resource %s", resource).isNotNull();
            return mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }
}
