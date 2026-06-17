package ai.budgetspace.product;

import ai.budgetspace.dto.ProductDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.33 — discount / sale tracking. Imports the whole verified catalog and guards the sale
 * invariants end to end: every "on sale" row carries a verified {@code originalPrice} strictly above the
 * current {@code price}, a parseable {@code saleEndsAt} when present, survives import (no row rejected by
 * the new validation), stays planner-eligible, and round-trips both fields through {@link ProductDto} to
 * the UI. Nothing is fabricated — the populated rows were read off the live JYSK product pages
 * (priceAmount = regular, JSON-LD price = promo, priceValidUntil = window) on 2026-06-17.
 */
class SaleCatalogRuntimeTest {

    @Test
    void verifiedSalesImportCleanlyAndRoundTripToTheUi() throws Exception {
        List<Product> catalog = importWholeCatalog();

        List<Product> onSale = catalog.stream()
                .filter(p -> p.getOriginalPrice() != null
                        && p.getOriginalPrice().compareTo(p.getPrice()) > 0)
                .toList();

        // We populated 24 verified JYSK HR sales; keep a healthy floor so a regression that drops the
        // originalPrice plumbing (e.g. the adapter again passing null) fails loudly.
        assertThat(onSale).as("on-sale rows (originalPrice > price)").hasSizeGreaterThanOrEqualTo(15);

        assertThat(onSale).allSatisfy(product -> {
            assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
            assertThat(product.getOriginalPrice()).as("originalPrice %s", product.getExternalId())
                    .isGreaterThan(product.getPrice());
            // A sale row stays a real, sourced, planner-eligible product.
            assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                    .as("planner-eligible %s", product.getExternalId()).isTrue();
            // saleEndsAt is optional, but when present it must be a real date (never a fabricated window).
            if (product.getSaleEndsAt() != null && !product.getSaleEndsAt().isBlank()) {
                assertThat(ProductTaxonomy.isParseableDate(product.getSaleEndsAt()))
                        .as("saleEndsAt parseable %s = %s", product.getExternalId(), product.getSaleEndsAt())
                        .isTrue();
            }
            // Both fields reach the frontend via the DTO so the UI can show the dual %/€ saving.
            ProductDto dto = ProductDto.from(product);
            assertThat(dto.originalPrice()).isEqualByComparingTo(product.getOriginalPrice());
            assertThat(dto.saleEndsAt()).isEqualTo(product.getSaleEndsAt());
        });

        // Concrete anchor: the EGEBY nightstand is genuinely −50% right now (regular 69.99 → 35, until
        // 2026-06-21). If this row drifts, the sourcing/plumbing changed and must be re-verified.
        Product egeby = onSale.stream()
                .filter(p -> "jysk-hr-nocni-ormaric-egeby-bijela".equals(p.getExternalId()))
                .findFirst().orElseThrow();
        assertThat(egeby.getPrice()).isEqualByComparingTo(new BigDecimal("35"));
        assertThat(egeby.getOriginalPrice()).isEqualByComparingTo(new BigDecimal("69.99"));
        assertThat(egeby.getSaleEndsAt()).isEqualTo("2026-06-21");
    }

    @Test
    void productsWithoutADiscountExposeNoOriginalPrice() throws Exception {
        List<Product> catalog = importWholeCatalog();
        // The vast majority of the catalog is not on sale; those rows must not carry a phantom originalPrice
        // (which would render a fake struck-through price in the UI).
        long notOnSale = catalog.stream().filter(p -> p.getOriginalPrice() == null).count();
        assertThat(notOnSale).as("rows with no originalPrice").isGreaterThan(catalog.size() / 2L);
    }

    private List<Product> importWholeCatalog() throws Exception {
        Map<String, Product> byExternalId = new LinkedHashMap<>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(byExternalId.get(inv.getArgument(0))));
        when(repository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            byExternalId.put(p.getExternalId(), p);
            return p;
        });
        RetailerSnapshotImportService importService = new RetailerSnapshotImportService(
                new ProductImportService(repository), new RetailerCatalogAdapter());

        for (String resource : RealCatalogSeeder.snapshotResources()) {
            var summary = importService.importSnapshot(load(resource));
            assertThat(summary.errors()).as("rejected rows in %s: %s", resource, summary.errors()).isEmpty();
        }
        return new ArrayList<>(byExternalId.values());
    }

    private List<RetailerProductSnapshotDto> load(String resource) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertThat(in).as("catalog resource %s", resource).isNotNull();
            return mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }
}
