package ai.budgetspace.pricewatch;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sprint 10.34 — registers the default price-drop delivery channel. The {@link ConditionalOnMissingBean}
 * means the log-only {@link LoggingPriceWatchNotifier} is used unless a real provider bean (SMTP /
 * SendGrid / Postmark, configured via backend env) is added later — that one then wins automatically.
 */
@Configuration
public class PriceWatchConfig {

    @Bean
    @ConditionalOnMissingBean(PriceWatchNotifier.class)
    public PriceWatchNotifier loggingPriceWatchNotifier() {
        return new LoggingPriceWatchNotifier();
    }
}
