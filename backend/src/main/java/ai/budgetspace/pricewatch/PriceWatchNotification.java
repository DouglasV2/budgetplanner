package ai.budgetspace.pricewatch;

import java.math.BigDecimal;

/**
 * Sprint 10.34 — the content of a single price-drop alert, handed to a {@link PriceWatchNotifier}.
 * Carries everything an email (or future push) needs, including the one-click unsubscribe token, so the
 * delivery channel stays a thin, swappable seam.
 */
public record PriceWatchNotification(
        String email,
        String productName,
        String productUrl,
        String market,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        int dropPercent,
        String unsubscribeToken
) {
}
