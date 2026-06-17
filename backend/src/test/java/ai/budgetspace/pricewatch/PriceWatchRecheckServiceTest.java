package ai.budgetspace.pricewatch;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PriceWatchRecheckServiceTest {

    private final Instant NOW = Instant.parse("2026-06-17T08:00:00Z");

    /** Capturing notifier — records every alert instead of sending. */
    private static final class CapturingNotifier implements PriceWatchNotifier {
        final List<PriceWatchNotification> sent = new ArrayList<>();
        @Override public void notifyPriceDrop(PriceWatchNotification n) { sent.add(n); }
    }

    /** Stub probe — returns a configured live price per product URL (empty = unavailable). */
    private static final class StubProbe implements LivePriceProbe {
        final Map<String, BigDecimal> prices = new HashMap<>();
        @Override public Optional<BigDecimal> currentPrice(String productUrl, String retailer) {
            return Optional.ofNullable(prices.get(productUrl));
        }
    }

    private PriceWatch watch(String url, String baseline, int threshold) {
        PriceWatch w = new PriceWatch();
        w.setId("w-" + url);
        w.setExternalId("ext-" + url);
        w.setProductName("Proizvod " + url);
        w.setProductUrl(url);
        w.setRetailer("JYSK");
        w.setMarket("HR");
        w.setEmail("ana@example.com");
        w.setBaselinePrice(new BigDecimal(baseline));
        w.setThresholdPercent(threshold);
        w.setActive(true);
        w.setUnsubscribeToken("tok-" + url);
        return w;
    }

    private PriceWatchRecheckService service(List<PriceWatch> watches, StubProbe probe, CapturingNotifier notifier) {
        PriceWatchRepository repo = mock(PriceWatchRepository.class);
        when(repo.findByActiveTrue()).thenReturn(watches);
        when(repo.save(any(PriceWatch.class))).thenAnswer(inv -> inv.getArgument(0));
        return new PriceWatchRecheckService(repo, probe, notifier, true, 7);
    }

    @Test
    void notifiesOnAVerifiedDropPastTheThreshold() {
        PriceWatch w = watch("u1", "100", 5);
        StubProbe probe = new StubProbe();
        probe.prices.put("u1", new BigDecimal("80")); // -20%
        CapturingNotifier notifier = new CapturingNotifier();

        var summary = service(List.of(w), probe, notifier).runRecheck(NOW);

        assertThat(summary.notified()).isEqualTo(1);
        assertThat(notifier.sent).hasSize(1);
        PriceWatchNotification n = notifier.sent.get(0);
        assertThat(n.dropPercent()).isEqualTo(20);
        assertThat(n.oldPrice()).isEqualByComparingTo("100");
        assertThat(n.newPrice()).isEqualByComparingTo("80");
        assertThat(n.unsubscribeToken()).isEqualTo("tok-u1");
        // baseline is the alert anchor; the watch records what it last alerted on.
        assertThat(w.getLastNotifiedPrice()).isEqualByComparingTo("80");
        assertThat(w.getLastNotifiedAt()).isNotBlank();
    }

    @Test
    void doesNotNotifyWhenTheDropIsBelowTheThreshold() {
        PriceWatch w = watch("u1", "100", 5);
        StubProbe probe = new StubProbe();
        probe.prices.put("u1", new BigDecimal("97")); // -3% < 5%
        CapturingNotifier notifier = new CapturingNotifier();

        var summary = service(List.of(w), probe, notifier).runRecheck(NOW);

        assertThat(summary.notified()).isZero();
        assertThat(notifier.sent).isEmpty();
    }

    @Test
    void doesNotNotifyWhenThereIsNoDrop() {
        PriceWatch w = watch("u1", "100", 5);
        StubProbe probe = new StubProbe();
        probe.prices.put("u1", new BigDecimal("105")); // price went up
        CapturingNotifier notifier = new CapturingNotifier();

        var summary = service(List.of(w), probe, notifier).runRecheck(NOW);

        assertThat(summary.drops()).isZero();
        assertThat(notifier.sent).isEmpty();
    }

    @Test
    void respectsTheCooldownWindow() {
        PriceWatch w = watch("u1", "100", 5);
        w.setLastNotifiedAt(NOW.minus(Duration.ofDays(2)).toString()); // notified 2 days ago, cooldown 7
        w.setLastNotifiedPrice(new BigDecimal("82"));
        StubProbe probe = new StubProbe();
        probe.prices.put("u1", new BigDecimal("80")); // still on sale, even slightly lower
        CapturingNotifier notifier = new CapturingNotifier();

        var summary = service(List.of(w), probe, notifier).runRecheck(NOW);

        assertThat(summary.drops()).isEqualTo(1);
        assertThat(summary.skippedCooldown()).isEqualTo(1);
        assertThat(notifier.sent).isEmpty();
    }

    @Test
    void doesNotReNotifyTheSameOrHigherPriceAfterTheCooldown() {
        PriceWatch w = watch("u1", "100", 5);
        w.setLastNotifiedAt(NOW.minus(Duration.ofDays(30)).toString()); // outside cooldown
        w.setLastNotifiedPrice(new BigDecimal("80"));
        StubProbe probe = new StubProbe();
        probe.prices.put("u1", new BigDecimal("80")); // same price as last alert
        CapturingNotifier notifier = new CapturingNotifier();

        var summary = service(List.of(w), probe, notifier).runRecheck(NOW);

        assertThat(notifier.sent).isEmpty();
        assertThat(summary.skippedCooldown()).isEqualTo(1);
    }

    @Test
    void reNotifiesOnAFurtherDropAfterTheCooldown() {
        PriceWatch w = watch("u1", "100", 5);
        w.setLastNotifiedAt(NOW.minus(Duration.ofDays(30)).toString());
        w.setLastNotifiedPrice(new BigDecimal("80"));
        StubProbe probe = new StubProbe();
        probe.prices.put("u1", new BigDecimal("70")); // dropped further
        CapturingNotifier notifier = new CapturingNotifier();

        var summary = service(List.of(w), probe, notifier).runRecheck(NOW);

        assertThat(summary.notified()).isEqualTo(1);
        assertThat(notifier.sent).hasSize(1);
        assertThat(w.getLastNotifiedPrice()).isEqualByComparingTo("70");
    }

    @Test
    void countsPriceUnavailableWhenTheProbeCannotRead() {
        PriceWatch w = watch("u1", "100", 5);
        StubProbe probe = new StubProbe(); // no price configured → empty
        CapturingNotifier notifier = new CapturingNotifier();

        var summary = service(List.of(w), probe, notifier).runRecheck(NOW);

        assertThat(summary.priceUnavailable()).isEqualTo(1);
        assertThat(notifier.sent).isEmpty();
    }

    @Test
    void percentDropRoundsHalfUp() {
        assertThat(PriceWatchRecheckService.percentDrop(new BigDecimal("69.99"), new BigDecimal("35")))
                .isEqualTo(50);
        assertThat(PriceWatchRecheckService.percentDrop(new BigDecimal("100"), new BigDecimal("75")))
                .isEqualTo(25);
    }
}
