package ai.budgetspace.collector;

import ai.budgetspace.dto.CollectorItemDto;
import ai.budgetspace.dto.CollectorRequestDto;
import ai.budgetspace.dto.DiscoveryRequestDto;
import ai.budgetspace.dto.DiscoveryResponseDto;
import ai.budgetspace.product.CollectorRunItemRepository;
import ai.budgetspace.product.CollectorRunRepository;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductImportService;
import ai.budgetspace.product.ProductRepository;
import ai.budgetspace.product.RetailerCatalogAdapter;
import ai.budgetspace.product.RetailerSnapshotImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the category discovery service. These ensure that the service extracts
 * reasonable product URLs from a category page, respects the limit, deduplicates
 * duplicates, skips unsupported domains and can run without invoking the collector
 * when collect=false.
 */
class CategoryDiscoveryServiceTest {
    @Test
    void returnsProductUrlsWithinLimitAndSkipsUnsupported() throws Exception {
        String categoryUrl = "https://www.ikea.com/hr/hr/c/living-room";
        String html = "<html>" +
                "<a href=\"/hr/hr/p/sofa-123\">Sofa</a>" +
                "<a href=\"https://www.ikea.com/hr/hr/p/table-999\">Table</a>" +
                "<a href=\"/hr/hr/p/sofa-123\">Duplicate</a>" +
                "<a href=\"https://evil.com/p/item\">Bad</a>" +
                "</html>";
        ProductPageFetcher fetcher = new FakeFetcher(Map.of(categoryUrl, ProductPageFetcher.FetchResult.ok(html)));
        // Create a dummy retailer collector service; its dependencies won't be called when collect=false.
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RetailerCollectorService collectorService = new RetailerCollectorService(
                fetcher,
                new RetailerProductParser(new ObjectMapper()),
                new RetailerSnapshotImportService(new ProductImportService(repository), new RetailerCatalogAdapter()),
                repository,
                new CollectorRunStore(mock(CollectorRunRepository.class), mock(CollectorRunItemRepository.class))
        );
        CategoryDiscoveryService discoveryService = new CategoryDiscoveryService(fetcher, collectorService);

        DiscoveryRequestDto req = new DiscoveryRequestDto("IKEA", categoryUrl, 3, false, null);
        DiscoveryResponseDto response = discoveryService.discover(req);

        assertThat(response.errors()).isEmpty();
        assertThat(response.foundUrls()).hasSize(2);
        // Should include the two valid ikea product URLs, deduplicated and in order
        assertThat(response.foundUrls().get(0)).contains("/p/sofa-123");
        assertThat(response.foundUrls().get(1)).contains("/p/table-999");
        // Should skip the unsupported domain
        assertThat(response.skippedUrls()).contains("https://evil.com/p/item");
        // Since collect=false, no collector summary is returned
        assertThat(response.collectorSummary()).isNull();
    }

    // Helper fetcher for tests
    private static final class FakeFetcher implements ProductPageFetcher {
        private final Map<String, FetchResult> responses;
        private FakeFetcher(Map<String, FetchResult> responses) {
            this.responses = responses;
        }
        @Override
        public FetchResult fetch(String url) {
            return responses.getOrDefault(url, FetchResult.error("URL nije konfiguriran u testu."));
        }
    }
}