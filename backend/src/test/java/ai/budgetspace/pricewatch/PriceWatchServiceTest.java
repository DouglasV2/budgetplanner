package ai.budgetspace.pricewatch;

import ai.budgetspace.dto.CreatePriceWatchRequest;
import ai.budgetspace.dto.PriceWatchDto;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceWatchServiceTest {

    private final PriceWatchRepository watchRepo = mock(PriceWatchRepository.class);
    private final ProductRepository productRepo = mock(ProductRepository.class);
    private final PriceWatchService service = new PriceWatchService(watchRepo, productRepo);

    private Product product(String externalId) {
        Product p = new Product();
        p.setId(externalId);
        p.setExternalId(externalId);
        p.setName("Noćni ormarić EGEBY");
        p.setRetailer("JYSK");
        p.setProductUrl("https://jysk.hr/p/egeby");
        p.setUrl("https://jysk.hr/p/egeby");
        p.setPrice(new BigDecimal("69.99"));
        p.setMarket("HR");
        return p;
    }

    @Test
    void createsAWatchWithExplicitConsentAndDefaultThreshold() {
        when(productRepo.findByExternalId("egeby")).thenReturn(Optional.of(product("egeby")));
        when(watchRepo.findByEmailIgnoreCaseAndExternalIdAndActiveTrue(anyString(), anyString())).thenReturn(Optional.empty());
        when(watchRepo.save(any(PriceWatch.class))).thenAnswer(inv -> inv.getArgument(0));

        PriceWatchDto dto = service.create(
                new CreatePriceWatchRequest("Ana@Example.com", "egeby", "HR", null, true), "sess-1");

        assertThat(dto.alreadyWatching()).isFalse();
        assertThat(dto.thresholdPercent()).isEqualTo(5); // owner default
        assertThat(dto.baselinePrice()).isEqualByComparingTo("69.99");

        ArgumentCaptor<PriceWatch> captor = ArgumentCaptor.forClass(PriceWatch.class);
        verify(watchRepo).save(captor.capture());
        PriceWatch saved = captor.getValue();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getEmail()).isEqualTo("Ana@Example.com");
        assertThat(saved.getRetailer()).isEqualTo("JYSK");
        assertThat(saved.getUnsubscribeToken()).isNotBlank();
        assertThat(saved.getConsentAt()).isNotBlank();
        assertThat(saved.getMarket()).isEqualTo("HR");
    }

    @Test
    void rejectsWhenConsentIsNotGiven() {
        assertThatThrownBy(() -> service.create(
                new CreatePriceWatchRequest("ana@example.com", "egeby", "HR", 10, false), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pristanak");
        verify(watchRepo, never()).save(any());
    }

    @Test
    void rejectsAMalformedEmail() {
        assertThatThrownBy(() -> service.create(
                new CreatePriceWatchRequest("not-an-email", "egeby", "HR", null, true), null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(watchRepo, never()).save(any());
    }

    @Test
    void rejectsWhenTheProductDoesNotExist() {
        when(productRepo.findByExternalId("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(
                new CreatePriceWatchRequest("ana@example.com", "ghost", "HR", null, true), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nije pronađen");
    }

    @Test
    void clampsAnOutOfRangeThreshold() {
        when(productRepo.findByExternalId("egeby")).thenReturn(Optional.of(product("egeby")));
        when(watchRepo.findByEmailIgnoreCaseAndExternalIdAndActiveTrue(anyString(), anyString())).thenReturn(Optional.empty());
        when(watchRepo.save(any(PriceWatch.class))).thenAnswer(inv -> inv.getArgument(0));

        PriceWatchDto dto = service.create(
                new CreatePriceWatchRequest("ana@example.com", "egeby", "HR", 999, true), null);
        assertThat(dto.thresholdPercent()).isEqualTo(90); // clamped to MAX
    }

    @Test
    void isIdempotentForAnExistingActiveWatch() {
        PriceWatch existing = new PriceWatch();
        existing.setId("w-1");
        existing.setEmail("ana@example.com");
        existing.setExternalId("egeby");
        existing.setThresholdPercent(5);
        existing.setBaselinePrice(new BigDecimal("69.99"));
        existing.setProductName("Noćni ormarić EGEBY");
        existing.setActive(true);
        when(productRepo.findByExternalId("egeby")).thenReturn(Optional.of(product("egeby")));
        when(watchRepo.findByEmailIgnoreCaseAndExternalIdAndActiveTrue(eq("ana@example.com"), eq("egeby")))
                .thenReturn(Optional.of(existing));

        PriceWatchDto dto = service.create(
                new CreatePriceWatchRequest("ana@example.com", "egeby", "HR", null, true), null);

        assertThat(dto.alreadyWatching()).isTrue();
        assertThat(dto.id()).isEqualTo("w-1");
        verify(watchRepo, never()).save(any()); // no duplicate created
    }

    @Test
    void unsubscribeDeactivatesTheWatchAndIsTokenSafe() {
        PriceWatch w = new PriceWatch();
        w.setUnsubscribeToken("tok-123");
        w.setActive(true);
        when(watchRepo.findByUnsubscribeToken("tok-123")).thenReturn(Optional.of(w));
        when(watchRepo.findByUnsubscribeToken("nope")).thenReturn(Optional.empty());

        assertThat(service.unsubscribe("tok-123")).containsEntry("status", "unsubscribed");
        assertThat(w.isActive()).isFalse();
        // An unknown token still reports success (so it can't be probed for validity).
        assertThat(service.unsubscribe("nope")).containsEntry("status", "unsubscribed");
    }
}
