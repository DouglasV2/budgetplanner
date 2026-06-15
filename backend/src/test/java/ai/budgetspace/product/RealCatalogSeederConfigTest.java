package ai.budgetspace.product;

import ai.budgetspace.dto.ImportSummaryDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.5 — the runtime seed must be safe and configurable:
 * <ul>
 *   <li>when disabled by flag it does nothing (sample catalog untouched), and</li>
 *   <li>when enabled it imports the real catalog and retires the fake sample living-room rows so
 *       homepage-link products never reach users.</li>
 * </ul>
 */
class RealCatalogSeederConfigTest {

    @Test
    void disabledFlagSkipsSeedingEntirely() {
        ProductRepository repository = mock(ProductRepository.class);
        RetailerSnapshotImportService importer = mock(RetailerSnapshotImportService.class);

        RealCatalogSeeder seeder = new RealCatalogSeeder(repository, importer, new ObjectMapper(), false);
        seeder.run(null);

        verify(importer, never()).importSnapshot(any());
        verify(repository, never()).findAll();
        verify(repository, never()).delete(any());
    }

    @Test
    void enabledFlagImportsRealCatalogAndRetiresFakeLivingRoom() {
        List<Product> db = new ArrayList<>();
        db.add(legacySampleLivingRoom("fake-living-room-sofa"));
        db.add(realImported("real-imported-sofa"));

        ProductRepository repository = mock(ProductRepository.class);
        // findAll returns a fresh copy each call (as in production) so deletes don't break iteration.
        when(repository.findAll()).thenAnswer(invocation -> new ArrayList<>(db));
        doAnswer(invocation -> {
            db.remove(invocation.getArgument(0));
            return null;
        }).when(repository).delete(any());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RetailerSnapshotImportService importer = mock(RetailerSnapshotImportService.class);
        when(importer.importSnapshot(any())).thenReturn(new ImportSummaryDto(76, 0, 0, 76, List.of(), List.of()));

        RealCatalogSeeder seeder = new RealCatalogSeeder(repository, importer, new ObjectMapper(), true);
        seeder.run(null);

        verify(importer).importSnapshot(any());
        assertThat(db).as("fake sample living-room product retired")
                .noneMatch(product -> "fake-living-room-sofa".equals(product.getId()));
        assertThat(db).as("real imported product kept")
                .anyMatch(product -> "real-imported-sofa".equals(product.getId()));
    }

    private Product legacySampleLivingRoom(String id) {
        Product product = base(id);
        product.setRoomTags("living-room");
        product.setSourceReference(null); // legacy sample rows from data.sql have no source reference
        product.setUrl("https://www.ikea.com/hr/hr/"); // homepage link — exactly what must not reach users
        return product;
    }

    private Product realImported(String id) {
        Product product = base(id);
        product.setRoomTags("living-room");
        product.setSourceReference("ikea-jysk-hr-living-room-production-pilot-10-3");
        product.setProductUrl("https://www.ikea.com/hr/hr/p/real-12345678/");
        return product;
    }

    private Product base(String id) {
        Product product = new Product();
        product.setId(id);
        product.setExternalId(id);
        product.setName("Sofa " + id);
        product.setRetailer("IKEA");
        product.setCategory("sofa");
        product.setPrice(BigDecimal.valueOf(299));
        product.setStyleTags("modern");
        product.setInStock(true);
        product.setAvailabilityStatus("in-stock");
        return product;
    }
}
