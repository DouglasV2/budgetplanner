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
 * Sprint 10.31 (EU depth) — proves the IT + FI bedroom + home-office gap-fill imports cleanly. Both markets
 * were thin (IT IKEA-only); this file ports verified IKEA bedroom + office SKUs via the global article-number
 * trick (per-market EUR price + verified og:image). Every row must be planner-eligible, and both markets must
 * gain a bedroom anchor (bed) and a home-office anchor (desk).
 */
class EuBedroomOfficeCatalogRuntimeTest {

    @Test
    void euBedroomOfficeCatalogImportsCleanlyAndDeepensItFi() throws Exception {
        List<RetailerProductSnapshotDto> snapshot = load("/catalog/real-eu-bedroom-office-10-31.json");
        assertThat(snapshot).as("EU bedroom/office rows").hasSizeGreaterThanOrEqualTo(40);

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
            assertThat(product.getRetailer()).isEqualTo("IKEA");
            assertThat(product.getMarket()).isIn("IT", "FI");
            assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
            assertThat(product.isImageVerified()).as("verified image %s", product.getExternalId()).isTrue();
            assertThat(product.getProductUrl()).startsWith("https://www.ikea.com/");
            assertThat(URI.create(product.getProductUrl()).getHost()).isNotBlank();
            assertThat(CatalogSourcePolicy.isPlannerEligible(product)).as("planner-eligible %s", product.getExternalId()).isTrue();
        });

        // Both markets gained a bedroom anchor (bed) and a home-office anchor (desk).
        for (String market : new String[] {"IT", "FI"}) {
            assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo(market); assertThat(p.getCategory()).isEqualTo("bed"); });
            assertThat(saved).anySatisfy(p -> { assertThat(p.getMarket()).isEqualTo(market); assertThat(p.getCategory()).isEqualTo("desk"); });
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
