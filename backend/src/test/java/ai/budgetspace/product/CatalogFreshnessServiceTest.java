package ai.budgetspace.product;

import ai.budgetspace.pricewatch.LivePriceProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.156 — the rolling catalog freshness re-check: a confidently-read price updates the row (and
 * clears any unverified sale), an unreadable page is flagged check-store (never given a fabricated price),
 * and EVERY checked row's lastCheckedAt advances so the oldest-first queue always progresses.
 */
class CatalogFreshnessServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z"); // -> day "2026-07-01"

    private ProductRepository repository;
    private LivePriceProbe probe;
    private CatalogFreshnessService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProductRepository.class);
        probe = mock(LivePriceProbe.class);
        service = new CatalogFreshnessService(repository, probe, true, 60);
    }

    private static Product product(String id, String url, String retailer, String price, String lastChecked) {
        Product p = new Product();
        p.setId(id);
        p.setExternalId(id);
        p.setName("Item " + id);
        p.setProductUrl(url);
        p.setUrl(url);
        p.setRetailer(retailer);
        p.setCategory("sofa");
        p.setPrice(new BigDecimal(price));
        p.setAvailabilityStatus("in-stock");
        p.setInStock(true);
        p.setLastCheckedAt(lastChecked);
        return p;
    }

    @Test
    void changedPriceIsWrittenAndAnyUnverifiedSaleCleared() {
        Product p = product("p1", "https://ikea/p1", "IKEA", "299.00", "2026-06-10");
        p.setOriginalPrice(new BigDecimal("399.00")); // was "on sale"; the probe reads only current price
        when(repository.findByOrderByLastCheckedAtAsc(any(Pageable.class))).thenReturn(List.of(p));
        when(probe.currentPrice("https://ikea/p1", "IKEA")).thenReturn(Optional.of(new BigDecimal("349.00")));

        CatalogFreshnessService.RefreshSummary summary = service.runRefresh(NOW);

        assertThat(p.getPrice()).isEqualByComparingTo("349.00");
        assertThat(p.getOriginalPrice()).as("no unverified promo asserted").isNull();
        assertThat(p.getAvailabilityStatus()).isEqualTo("in-stock");
        assertThat(p.getLastCheckedAt()).isEqualTo("2026-07-01");
        assertThat(summary.changed()).isEqualTo(1);
        assertThat(summary.checked()).isEqualTo(1);
        verify(repository).save(p);
    }

    @Test
    void unreadablePageIsFlaggedCheckStoreWithNoFabricatedPriceButQueueStillProgresses() {
        Product p = product("p2", "https://blocked/p2", "SomeChain", "150.00", "2026-06-01");
        when(repository.findByOrderByLastCheckedAtAsc(any(Pageable.class))).thenReturn(List.of(p));
        when(probe.currentPrice("https://blocked/p2", "SomeChain")).thenReturn(Optional.empty());

        CatalogFreshnessService.RefreshSummary summary = service.runRefresh(NOW);

        assertThat(p.getPrice()).as("never fabricate a price we couldn't read").isEqualByComparingTo("150.00");
        assertThat(p.getAvailabilityStatus()).isEqualTo("check-store"); // UI keeps its honest hedge
        assertThat(p.getLastCheckedAt()).as("queue still advances so it can't wedge the front").isEqualTo("2026-07-01");
        assertThat(summary.unverifiable()).isEqualTo(1);
        assertThat(summary.changed()).isZero();
    }

    @Test
    void unreadableButDeadUrlIsAutoRetiredNotJustHedged() {
        // Sprint 10.167: the page won't price AND the URL is unambiguously gone (probe.liveness == DEAD) — retire
        // it (unavailable + out-of-stock) so canEnterPlanner drops it, instead of leaving a dead link forever.
        Product p = product("p4", "https://ikea/p4-gone", "IKEA", "120.00", "2026-06-02");
        when(repository.findByOrderByLastCheckedAtAsc(any(Pageable.class))).thenReturn(List.of(p));
        when(probe.currentPrice("https://ikea/p4-gone", "IKEA")).thenReturn(Optional.empty());
        when(probe.liveness("https://ikea/p4-gone", "IKEA")).thenReturn(LivePriceProbe.Liveness.DEAD);

        CatalogFreshnessService.RefreshSummary summary = service.runRefresh(NOW);

        assertThat(p.getAvailabilityStatus()).isEqualTo("unavailable");
        assertThat(p.isInStock()).isFalse();
        assertThat(p.getPrice()).as("never fabricate; keep the last known price on the retired row").isEqualByComparingTo("120.00");
        assertThat(p.getLastCheckedAt()).isEqualTo("2026-07-01");
        assertThat(summary.retired()).isEqualTo(1);
        assertThat(summary.unverifiable()).isZero();
    }

    @Test
    void unreadableAndUnknownLivenessStaysCheckStore() {
        // Blocked/anti-bot/transient (liveness UNKNOWN) must NOT be retired on a guess — only hedged check-store.
        Product p = product("p5", "https://blocked/p5", "SomeChain", "80.00", "2026-06-03");
        when(repository.findByOrderByLastCheckedAtAsc(any(Pageable.class))).thenReturn(List.of(p));
        when(probe.currentPrice("https://blocked/p5", "SomeChain")).thenReturn(Optional.empty());
        when(probe.liveness("https://blocked/p5", "SomeChain")).thenReturn(LivePriceProbe.Liveness.UNKNOWN);

        CatalogFreshnessService.RefreshSummary summary = service.runRefresh(NOW);

        assertThat(p.getAvailabilityStatus()).isEqualTo("check-store");
        assertThat(p.isInStock()).isTrue();
        assertThat(summary.retired()).isZero();
        assertThat(summary.unverifiable()).isEqualTo(1);
    }

    @Test
    void unchangedPriceIsConfirmedAndReStamped() {
        Product p = product("p3", "https://jysk/p3", "JYSK", "99.99", "2026-06-05");
        when(repository.findByOrderByLastCheckedAtAsc(any(Pageable.class))).thenReturn(List.of(p));
        when(probe.currentPrice("https://jysk/p3", "JYSK")).thenReturn(Optional.of(new BigDecimal("99.99")));

        CatalogFreshnessService.RefreshSummary summary = service.runRefresh(NOW);

        assertThat(p.getPrice()).isEqualByComparingTo("99.99");
        assertThat(p.getLastCheckedAt()).isEqualTo("2026-07-01");
        assertThat(summary.confirmed()).isEqualTo(1);
        assertThat(summary.changed()).isZero();
    }

    @Test
    void anImplausiblePriceJumpIsRefusedInsteadOfWritten() {
        // Sprint 10.193: kopalnica-online.si publishes offers.price in MINOR units ("28450" for 284,50 EUR) in
        // the very @type Product block the probe reads. Writing that would put a 100x price in front of users,
        // so a jump no real repricing produces is refused and the row is hedged instead.
        Product p = product("p6", "https://shop/p6", "Kopalnica Online", "284.50", "2026-06-04");
        when(repository.findByOrderByLastCheckedAtAsc(any(Pageable.class))).thenReturn(List.of(p));
        when(probe.currentPrice("https://shop/p6", "Kopalnica Online")).thenReturn(Optional.of(new BigDecimal("28450")));

        CatalogFreshnessService.RefreshSummary summary = service.runRefresh(NOW);

        assertThat(p.getPrice()).as("the stored price survives an implausible read").isEqualByComparingTo("284.50");
        assertThat(p.getAvailabilityStatus()).isEqualTo("check-store");
        assertThat(p.getLastCheckedAt()).as("queue still advances").isEqualTo("2026-07-01");
        assertThat(summary.rejected()).isEqualTo(1);
        assertThat(summary.changed()).isZero();
    }

    @Test
    void aPriceCollapsingByAFactorOfAHundredIsAlsoRefused() {
        // The mirror case: a shop that starts publishing MAJOR units where it used to publish minor ones.
        Product p = product("p7", "https://shop/p7", "Kopalnica Online", "1699.00", "2026-06-04");
        when(repository.findByOrderByLastCheckedAtAsc(any(Pageable.class))).thenReturn(List.of(p));
        when(probe.currentPrice("https://shop/p7", "Kopalnica Online")).thenReturn(Optional.of(new BigDecimal("1.699")));

        CatalogFreshnessService.RefreshSummary summary = service.runRefresh(NOW);

        assertThat(p.getPrice()).isEqualByComparingTo("1699.00");
        assertThat(summary.rejected()).isEqualTo(1);
    }

    @Test
    void aRealClearanceCutIsStillWritten() {
        // The guard must not swallow genuine repricing — a 70% clearance is well inside the plausible band.
        Product p = product("p8", "https://jysk/p8", "JYSK", "400.00", "2026-06-04");
        when(repository.findByOrderByLastCheckedAtAsc(any(Pageable.class))).thenReturn(List.of(p));
        when(probe.currentPrice("https://jysk/p8", "JYSK")).thenReturn(Optional.of(new BigDecimal("120.00")));

        CatalogFreshnessService.RefreshSummary summary = service.runRefresh(NOW);

        assertThat(p.getPrice()).isEqualByComparingTo("120.00");
        assertThat(summary.changed()).isEqualTo(1);
        assertThat(summary.rejected()).isZero();
    }

    @Test
    void scheduledRunDoesNothingWhenDisabled() {
        CatalogFreshnessService dormant = new CatalogFreshnessService(repository, probe, false, 60);

        dormant.scheduledRefresh();

        verify(repository, never()).findByOrderByLastCheckedAtAsc(any());
        verify(probe, never()).currentPrice(any(), any());
    }
}
