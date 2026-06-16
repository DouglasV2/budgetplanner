package ai.budgetspace.feed;

import ai.budgetspace.product.CatalogSourcePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sprint 10.14 — registers a default {@link RetailerFeed} slot for every feed-required retailer
 * (Decathlon, Pevex, Lesnina). Each defaults to {@link ConfigBackedRetailerFeed}, which is
 * unconfigured until an operator supplies a feed URL via the environment.
 *
 * <p>A real official/partner integration replaces one of these beans with its own
 * {@link RetailerFeed} implementation — nothing else needs to change, because
 * {@link RetailerFeedImporter} consumes every {@code RetailerFeed} bean.</p>
 */
@Configuration
public class RetailerFeedConfig {

    @Bean
    public RetailerFeed decathlonFeed(RetailerFeedProperties properties) {
        return new ConfigBackedRetailerFeed("Decathlon", CatalogSourcePolicy.SOURCE_OFFICIAL_FEED, properties);
    }

    @Bean
    public RetailerFeed pevexFeed(RetailerFeedProperties properties) {
        return new ConfigBackedRetailerFeed("Pevex", CatalogSourcePolicy.SOURCE_OFFICIAL_FEED, properties);
    }

    @Bean
    public RetailerFeed lesninaFeed(RetailerFeedProperties properties) {
        return new ConfigBackedRetailerFeed("Lesnina", CatalogSourcePolicy.SOURCE_OFFICIAL_FEED, properties);
    }
}
