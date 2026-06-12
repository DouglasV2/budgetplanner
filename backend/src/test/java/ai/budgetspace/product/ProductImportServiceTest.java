package ai.budgetspace.product;

import ai.budgetspace.dto.ImportProductDto;
import ai.budgetspace.dto.ImportSummaryDto;
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

class ProductImportServiceTest {
    @Test
    void skipsInvalidProductsButImportsValidOnes() {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProductImportService service = new ProductImportService(repository);

        ImportProductDto valid = product("valid-1", "Dvosjed svijetli tekstil", "IKEA", "kauč", BigDecimal.valueOf(249), List.of("moderno", "clean"), List.of("dnevni boravak"));
        ImportProductDto invalid = product("bad-1", "Loš proizvod", "Nepoznata", "sofa", BigDecimal.ZERO, List.of("modern"), List.of("living-room"));

        ImportSummaryDto summary = service.importProducts(List.of(valid, invalid));

        assertThat(summary.created()).isEqualTo(1);
        assertThat(summary.updated()).isZero();
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.totalReceived()).isEqualTo(2);
        assertThat(summary.products()).hasSize(1);
        assertThat(summary.errors()).anySatisfy(error -> {
            assertThat(error.row()).isEqualTo(2);
            assertThat(error.externalId()).isEqualTo("bad-1");
            assertThat(error.message()).contains("Trgovina nije podržana");
        });
    }

    @Test
    void duplicateExternalIdUpdatesExistingProduct() {
        Product existing = existingProduct("dup-1");
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId("dup-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProductImportService service = new ProductImportService(repository);

        ImportSummaryDto summary = service.importProducts(List.of(
                product("dup-1", "Ažurirani dvosjed", "JYSK", "sofa", BigDecimal.valueOf(399), List.of("cozy"), List.of("living-room"))
        ));

        assertThat(summary.created()).isZero();
        assertThat(summary.updated()).isEqualTo(1);
        assertThat(summary.skipped()).isZero();
        assertThat(existing.getName()).isEqualTo("Ažurirani dvosjed");
        assertThat(existing.getRetailer()).isEqualTo("JYSK");
        assertThat(existing.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(399));
    }

    @Test
    void csvImportTrimsValuesAndSupportsPipeOrCommaTags() {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProductImportService service = new ProductImportService(repository);

        String csv = "externalId,name,retailer,category,price,styleTags,roomTags,productUrl,imageUrl,availabilityStatus\n" +
                " csv-1 , Stolna lampa crna , Pevex , lampa , 39.99 , modern|industrial , home-office|living-room , https://example.com/p1 , https://example.com/i1.jpg , check-store\n" +
                "csv-2,Tepih prirodni ton,JYSK,tepih,79.99,\"cozy,natural\",\"living-room,bedroom\",https://example.com/p2,https://example.com/i2.jpg,limited\n";

        ImportSummaryDto summary = service.importCsv(csv);

        assertThat(summary.created()).isEqualTo(2);
        assertThat(summary.skipped()).isZero();
        assertThat(summary.totalReceived()).isEqualTo(2);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getStyleTags()).isEqualTo("modern,industrial");
        assertThat(captor.getAllValues().get(0).getRoomTags()).isEqualTo("home-office,living-room");
        assertThat(captor.getAllValues().get(1).getStyleTags()).isEqualTo("cozy,boho");
        assertThat(captor.getAllValues().get(1).getRoomTags()).isEqualTo("living-room,bedroom");
    }

    @Test
    void csvImportReturnsUsefulMissingHeaderError() {
        ProductRepository repository = mock(ProductRepository.class);
        ProductImportService service = new ProductImportService(repository);

        ImportSummaryDto summary = service.importCsv("externalId,name,retailer,category,price\nmissing-tags,Test,IKEA,sofa,10\n");

        assertThat(summary.created()).isZero();
        assertThat(summary.skipped()).isZero();
        assertThat(summary.totalReceived()).isZero();
        assertThat(summary.errors()).singleElement().satisfies(error ->
                assertThat(error.message()).contains("styleTags", "roomTags")
        );
    }

    private ImportProductDto product(String externalId, String name, String retailer, String category, BigDecimal price, List<String> styleTags, List<String> roomTags) {
        return new ImportProductDto(
                null,
                externalId,
                name,
                retailer,
                category,
                price,
                null,
                styleTags,
                roomTags,
                "https://example.com/image.jpg",
                "https://example.com/product",
                "in-stock",
                null,
                "2026-06-12",
                "standard",
                "Dobar omjer cijene i korisnosti."
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
        product.setOriginalPrice(null);
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
        product.setRating(4.1);
        product.setInStock(true);
        product.setNote("Stari opis.");
        return product;
    }
}
