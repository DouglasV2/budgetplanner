package ai.budgetspace.pricewatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sprint 10.34 — default, no-send notifier: it logs the alert instead of emailing. This is the "seam now,
 * provider later" delivery channel (owner decision): a real email provider (SMTP / SendGrid / Postmark)
 * is added later as another {@link PriceWatchNotifier} bean, configured by backend env, and — because
 * {@link PriceWatchConfig} registers this default with {@code @ConditionalOnMissingBean} — automatically
 * takes over. No credentials are committed, and the re-check pipeline is fully exercisable today without
 * sending anything.
 */
public class LoggingPriceWatchNotifier implements PriceWatchNotifier {
    private static final Logger log = LoggerFactory.getLogger(LoggingPriceWatchNotifier.class);

    @Override
    public void notifyPriceDrop(PriceWatchNotification n) {
        // Mask the local part of the email so the log does not leak the full address.
        log.info("Price-drop alert (log-only): product='{}' {} {} -> {} (-{}%) to {} [unsubscribe token={}]",
                n.productName(), n.market(), n.oldPrice(), n.newPrice(), n.dropPercent(),
                maskEmail(n.email()), n.unsubscribeToken());
    }

    private String maskEmail(String email) {
        if (email == null) return "?";
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.charAt(0) + "***" + email.substring(at);
    }
}
