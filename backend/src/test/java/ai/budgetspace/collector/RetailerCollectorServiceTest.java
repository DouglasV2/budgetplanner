package ai.budgetspace.collector;

import ai.budgetspace.dto.CollectorDefaultsDto;
import ai.budgetspace.dto.CollectorItemDto;
import ai.budgetspace.dto.CollectorRequestDto;
import ai.budgetspace.dto.CollectorRunSummaryDto;
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
import java.util.ArrayList;
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

class RetailerCollectorServiceTest {
    private final CollectorDefaultsDto defaults = new CollectorDefaultsDto("sofa", List.of("living-room"), List.of("modern"), "test-run");

    @Test
    void reportsImportedNeedsReviewAndSkippedAndKeepsRunningWhenOneUrlFails() throws Exception {
        String okUrl = "https://example.com/p/sofa-123";
        String noPriceUrl = "https://example.com/p/rug";
        String brokenUrl = "https://example.com/p/broken";

        ProductPageFetcher fetcher = new FakeFetcher(Map.of(
                okUrl, ProductPageFetcher.FetchResult.ok(fixture("generic-product-jsonld.html")),
                noPriceUrl, ProductPageFetcher.FetchResult.ok(fixture("missing-price.html")),
                brokenUrl, ProductPageFetcher.FetchResult.error("Stranica nije dostupna.")
        ));
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RetailerCollectorService service = service(fetcher, repository);

        CollectorRunSummaryDto summary = service.collect(
                new CollectorRequestDto("JYSK", List.of(okUrl, noPriceUrl, brokenUrl), defaults, null));

        assertThat(summary.runId()).isNotBlank();
        assertThat(summary.totalReceived()).isEqualTo(3);
        assertThat(summary.fetched()).isEqualTo(2);
        assertThat(summary.parsed()).isEqualTo(2);
        assertThat(summary.imported()).isEqualTo(1);
        assertThat(summary.needsReview()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.errors()).anySatisfy(error -> assertThat(error.url()).isEqualTo(brokenUrl));
        assertThat(summary.reviewItems()).anySatisfy(item -> assertThat(item.missingFields()).contains("price"));
        assertThat(summary.warnings()).isNotEmpty();
        assertThat(summary.products()).extracting(report -> report.status())
                .contains("imported", "needs-review", "skipped");

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSourceType()).isEqualTo("future-scraper");
    }

    @Test
    void itemsFormatLetsEachUrlKeepItsOwnCategory() throws Exception {
        String sofaUrl = "https://www.ikea.com/hr/hr/p/dvosjed-1";
        String tvUrl = "https://www.ikea.com/hr/hr/p/tv-komoda-1";

        ProductPageFetcher fetcher = new FakeFetcher(Map.of(
                sofaUrl, ProductPageFetcher.FetchResult.ok(fixture("generic-product-og.html")),
                tvUrl, ProductPageFetcher.FetchResult.ok(fixture("generic-product-og.html"))
        ));
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RetailerCollectorService service = service(fetcher, repository);

        CollectorRequestDto request = new CollectorRequestDto("IKEA", null, defaults, List.of(
                new CollectorItemDto(sofaUrl, new CollectorDefaultsDto("sofa", null, null, null)),
                new CollectorItemDto(tvUrl, new CollectorDefaultsDto("tv-unit", null, null, null))
        ));

        CollectorRunSummaryDto summary = service.collect(request);

        assertThat(summary.totalReceived()).isEqualTo(2);
        assertThat(summary.imported()).isEqualTo(2);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Product::getCategory)
                .containsExactlyInAnyOrder("sofa", "tv-unit");
    }

    @Test
    void backwardCompatibleUrlsFormatStillWorks() throws Exception {
        String okUrl = "https://example.com/p/sofa-123";
        ProductPageFetcher fetcher = new FakeFetcher(Map.of(
                okUrl, ProductPageFetcher.FetchResult.ok(fixture("generic-product-jsonld.html"))));
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RetailerCollectorService service = service(fetcher, repository);

        CollectorRunSummaryDto summary = service.collect(
                new CollectorRequestDto("JYSK", List.of(okUrl), defaults, null));

        assertThat(summary.imported()).isEqualTo(1);
        assertThat(summary.totalReceived()).isEqualTo(1);
    }

    @Test
    void rejectsTooManyUrls() {
        ProductRepository repository = mock(ProductRepository.class);
        RetailerCollectorService service = service(new FakeFetcher(Map.of()), repository);

        List<String> urls = new ArrayList<>();
        for (int i = 0; i < 21; i++) urls.add("https://example.com/p/" + i);

        CollectorRunSummaryDto summary = service.collect(new CollectorRequestDto("IKEA", urls, defaults, null));

        assertThat(summary.imported()).isZero();
        assertThat(summary.errors()).anySatisfy(error -> assertThat(error.message()).contains("Najviše"));
    }

    @Test
    void rejectsUnsupportedRetailer() {
        ProductRepository repository = mock(ProductRepository.class);
        RetailerCollectorService service = service(new FakeFetcher(Map.of()), repository);

        CollectorRunSummaryDto summary = service.collect(
                new CollectorRequestDto("Nepoznata", List.of("https://example.com/p/x"), defaults, null));

        assertThat(summary.imported()).isZero();
        assertThat(summary.errors()).anySatisfy(error -> assertThat(error.message()).contains("Trgovina nije podržana"));
    }

    @Test
    void retryRequestContainsOnlyNeedsReviewItems() throws Exception {
        String okUrl = "https://example.com/p/sofa-123";
        String noPriceUrl = "https://example.com/p/rug";
        ProductPageFetcher fetcher = new FakeFetcher(Map.of(
                okUrl, ProductPageFetcher.FetchResult.ok(fixture("generic-product-jsonld.html")),
                noPriceUrl, ProductPageFetcher.FetchResult.ok(fixture("missing-price.html"))));
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RetailerCollectorService service = service(fetcher, repository);

        CollectorRunSummaryDto summary = service.collect(
                new CollectorRequestDto("JYSK", List.of(okUrl, noPriceUrl), defaults, null));

        assertThat(summary.retryRequest()).isNotNull();
        assertThat(summary.retryRequest().items()).extracting(CollectorItemDto::url).containsExactly(noPriceUrl);
    }

    @Test
    void itemWithoutUrlIsRejected() {
        ProductRepository repository = mock(ProductRepository.class);
        RetailerCollectorService service = service(new FakeFetcher(Map.of()), repository);

        CollectorRunSummaryDto summary = service.collect(new CollectorRequestDto("IKEA", null, defaults, List.of(
                new CollectorItemDto("https://example.com/p/ok", new CollectorDefaultsDto("sofa", null, null, null)),
                new CollectorItemDto("   ", new CollectorDefaultsDto("rug", null, null, null)))));

        assertThat(summary.imported()).isZero();
        assertThat(summary.errors()).anySatisfy(error -> assertThat(error.message()).contains("Svaki item mora imati url"));
    }

    private RetailerCollectorService service(ProductPageFetcher fetcher, ProductRepository repository) {
        RetailerSnapshotImportService snapshotImportService =
                new RetailerSnapshotImportService(new ProductImportService(repository), new RetailerCatalogAdapter());
        CollectorRunStore runStore = new CollectorRunStore(mock(CollectorRunRepository.class), mock(CollectorRunItemRepository.class));
        return new RetailerCollectorService(fetcher, new RetailerProductParser(new ObjectMapper()), snapshotImportService, repository, runStore);
    }

    private String fixture(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/collector/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

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
