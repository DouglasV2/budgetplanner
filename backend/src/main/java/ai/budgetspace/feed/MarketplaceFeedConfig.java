package ai.budgetspace.feed;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sprint 10.49 — registers a default {@link MarketplaceFeed} placeholder for every second-hand consumer
 * marketplace, one per market (plus the multi-market eBay / Facebook Marketplace). Each defaults to
 * {@link ConfigBackedMarketplaceFeed}, unconfigured until a compliant feed URL is supplied via the
 * environment. {@link RetailerFeedImporter} consumes every {@code RetailerFeed} bean, so these are skipped
 * cleanly on startup and import nothing — they are the "Rabljeno" slots a real API/partner/affiliate feed
 * (e.g. an eBay Browse API client) drops into later. Never scraped.
 */
@Configuration
public class MarketplaceFeedConfig {

    private static MarketplaceFeed placeholder(String marketplace, String market, MarketplaceFeedProperties properties) {
        return new ConfigBackedMarketplaceFeed(marketplace, market, properties);
    }

    @Bean
    public RetailerFeed njuskaloMarketplaceFeed(MarketplaceFeedProperties properties) {
        return placeholder("Njuškalo", "HR", properties);
    }

    @Bean
    public RetailerFeed facebookMarketplaceFeed(MarketplaceFeedProperties properties) {
        return placeholder("Facebook Marketplace", null, properties);
    }

    /**
     * Sprint 10.51: the real eBay Browse API feed — the first compliant "Rabljeno" source. It replaces the
     * eBay placeholder but behaves identically until an operator sets the eBay App ID + Cert ID in the
     * environment ({@link EbayBrowseFeedProperties}): dormant, no network call, imports nothing. Never scraped.
     */
    @Bean
    public RetailerFeed ebayMarketplaceFeed(EbayBrowseFeedProperties ebayProperties) {
        return new EbayBrowseFeed(ebayProperties);
    }

    @Bean
    public RetailerFeed bolhaMarketplaceFeed(MarketplaceFeedProperties properties) {
        return placeholder("Bolha", "SI", properties);
    }

    @Bean
    public RetailerFeed willhabenMarketplaceFeed(MarketplaceFeedProperties properties) {
        return placeholder("Willhaben", "AT", properties);
    }

    @Bean
    public RetailerFeed kleinanzeigenMarketplaceFeed(MarketplaceFeedProperties properties) {
        return placeholder("Kleinanzeigen", "DE", properties);
    }

    @Bean
    public RetailerFeed subitoMarketplaceFeed(MarketplaceFeedProperties properties) {
        return placeholder("Subito", "IT", properties);
    }

    @Bean
    public RetailerFeed toriMarketplaceFeed(MarketplaceFeedProperties properties) {
        return placeholder("Tori", "FI", properties);
    }

    @Bean
    public RetailerFeed leboncoinMarketplaceFeed(MarketplaceFeedProperties properties) {
        return placeholder("Leboncoin", "FR", properties);
    }

    @Bean
    public RetailerFeed marktplaatsMarketplaceFeed(MarketplaceFeedProperties properties) {
        return placeholder("Marktplaats", "NL", properties);
    }

    @Bean
    public RetailerFeed bazosMarketplaceFeed(MarketplaceFeedProperties properties) {
        return placeholder("Bazoš", "SK", properties);
    }

    @Bean
    public RetailerFeed wallapopMarketplaceFeed(MarketplaceFeedProperties properties) {
        return placeholder("Wallapop", "ES", properties);
    }

    @Bean
    public RetailerFeed olxMarketplaceFeed(MarketplaceFeedProperties properties) {
        return placeholder("OLX", "PT", properties);
    }

    @Bean
    public RetailerFeed finnMarketplaceFeed(MarketplaceFeedProperties properties) {
        return placeholder("Finn", "NO", properties);
    }

    @Bean
    public RetailerFeed blocketMarketplaceFeed(MarketplaceFeedProperties properties) {
        return placeholder("Blocket", "SE", properties);
    }

    @Bean
    public RetailerFeed dbaMarketplaceFeed(MarketplaceFeedProperties properties) {
        return placeholder("DBA", "DK", properties);
    }
}
