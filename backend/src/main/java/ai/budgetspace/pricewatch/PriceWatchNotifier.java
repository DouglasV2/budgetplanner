package ai.budgetspace.pricewatch;

/**
 * Sprint 10.34 — the delivery seam for price-drop alerts. Email is the first channel (push later); like
 * the {@code RetailerFeed} and {@code LlmClient} seams, the concrete provider plugs in via configuration
 * and backend env (never committed credentials). The default {@link LoggingPriceWatchNotifier} only logs,
 * so the whole feature is testable and safe to run with no provider configured.
 */
public interface PriceWatchNotifier {
    void notifyPriceDrop(PriceWatchNotification notification);
}
