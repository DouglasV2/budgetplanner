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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.13 — proves the verified Emmezeta HR snapshot imports cleanly through the runtime
 * pipeline (real name/price/URL; no fabrication). Emmezeta is the only one of the additional HR
 * retailers (Emmezeta, Lesnina, Decathlon, Pevex) whose product pages allow automated price
 * verification — the others return HTTP 403, so they need an official feed/collector instead.
 */
class EmmezetaCatalogRuntimeTest {
    private static final String RESOURCE = "/catalog/real-emmezeta-hr.json";

    @Test
    void emmezetaSnapshotImportsCleanlyWithRealUrls() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = load();
        List<Product> saved = new ArrayList<>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(java.util.Optional.empty());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            saved.add(product);
            return product;
        });

        ImportSummaryDto summary = new RetailerSnapshotImportService(
                new ProductImportService(repository), new RetailerCatalogAdapter()).importSnapshot(snapshot);

        assertThat(summary.errors()).as("import errors").isEmpty();
        assertThat(saved).isNotEmpty();
        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getRetailer()).isEqualTo("Emmezeta");
            assertThat(product.getPrice().signum()).isPositive();
            assertThat(URI.create(product.getProductUrl()).getHost()).endsWith("emmezeta.hr");
        });
        // The mint sofa carries a green colour tag so colour-preference matching can use it.
        assertThat(saved).anySatisfy(product ->
                assertThat(product.getColorTags() == null ? "" : product.getColorTags()).contains("green"));
    }

    private List<RetailerProductSnapshotDto> load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(RESOURCE)) {
            assertThat(in).as("catalog resource %s", RESOURCE).isNotNull();
            return mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }
}
