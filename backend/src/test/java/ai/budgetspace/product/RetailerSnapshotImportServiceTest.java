package ai.budgetspace.product;

import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetailerSnapshotImportServiceTest {

    private RetailerSnapshotImportService serviceWith(ProductRepository repository) {
        return new RetailerSnapshotImportService(new ProductImportService(repository), new RetailerCatalogAdapter());
    }

    @Test
    void normalisesSnapshotAndReusesImportValidation() {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RetailerSnapshotImportService service = serviceWith(repository);

        RetailerProductSnapshotDto valid = snapshot("snap-1", "Dvosjed svijetli tekstil", "IKEA", "kauč", BigDecimal.valueOf(249), List.of("dnevni boravak"), List.of("moderno", "clean"), "in-stock");
        RetailerProductSnapshotDto invalid = snapshot("snap-bad", "Loš proizvod", "Nepoznata", "sofa", BigDecimal.ZERO, List.of("living-room"), List.of("modern"), "in-stock");

        ImportSummaryDto summary = service.importSnapshot(List.of(valid, invalid));

        assertThat(summary.created()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.totalReceived()).isEqualTo(2);
        assertThat(summary.errors()).anySatisfy(error -> assertThat(error.message()).contains("Trgovina nije podržana"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repository).save(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.getCategory()).isEqualTo("sofa");
        assertThat(saved.getStyleTags()).isEqualTo("modern,minimal");
        assertThat(saved.getRoomTags()).isEqualTo("living-room");
        assertThat(saved.getAvailabilityStatus()).isEqualTo("in-stock");
    }

    @Test
    void duplicateExternalIdUpdatesInsteadOfCreating() {
        Product existing = existingProduct("snap-dup");
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId("snap-dup")).thenReturn(Optional.of(existing));
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RetailerSnapshotImportService service = serviceWith(repository);

        ImportSummaryDto summary = service.importSnapshot(List.of(
                snapshot("snap-dup", "Ažurirani trosjed", "JYSK", "sofa", BigDecimal.valueOf(399), List.of("living-room"), List.of("cozy"), "limited")
        ));

        assertThat(summary.created()).isZero();
        assertThat(summary.updated()).isEqualTo(1);
        assertThat(summary.skipped()).isZero();
        assertThat(existing.getName()).isEqualTo("Ažurirani trosjed");
        assertThat(existing.getRetailer()).isEqualTo("JYSK");
        assertThat(existing.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(399));
        assertThat(existing.getAvailabilityStatus()).isEqualTo("limited");
        assertThat(existing.isInStock()).isTrue();
    }

    @Test
    void emptySnapshotReturnsErrorSummaryWithoutSaving() {
        ProductRepository repository = mock(ProductRepository.class);
        RetailerSnapshotImportService service = serviceWith(repository);

        ImportSummaryDto summary = service.importSnapshot(List.of());

        assertThat(summary.created()).isZero();
        assertThat(summary.errors()).isNotEmpty();
    }

    private RetailerProductSnapshotDto snapshot(String externalId, String name, String retailer, String category, BigDecimal price, List<String> roomTags, List<String> styleTags, String availability) {
        return new RetailerProductSnapshotDto(
                externalId,
                name,
                retailer,
                category,
                price,
                "https://example.com/p/" + externalId,
                "https://example.com/i/" + externalId + ".jpg",
                availability,
                "Provjeri dostavu ili preuzimanje prije kupnje.",
                roomTags,
                styleTags,
                "standard"
        );
    }

    private Product existingProduct(String externalId) {
        Product product = new Product();
        product.setId("existing-id");
        product.setExternalId(externalId);
        product.setName("Stari naziv");
        product.setRetailer("IKEA");
        product.setCategory("sofa");
        product.setPrice(BigDecimal.valueOf(299));
        product.setStyleTags("modern");
        product.setRoomTags("living-room");
        product.setImageUrl("https://example.com/image.jpg");
        product.setProductUrl("https://example.com/product");
        product.setImage("https://example.com/image.jpg");
        product.setUrl("https://example.com/product");
        product.setAvailabilityStatus("in-stock");
        product.setDeliveryNote("Provjeri dostavu ili preuzimanje prije kupnje.");
        product.setLastCheckedAt("2026-06-12");
        product.setPriceTier("standard");
        product.setRating(4.0);
        product.setInStock(true);
        product.setNote("Stari opis.");
        return product;
    }
}
