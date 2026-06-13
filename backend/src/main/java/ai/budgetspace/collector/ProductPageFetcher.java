package ai.budgetspace.collector;

/**
 * Fetches the raw HTML of a single product page. This is the only part of the collector
 * that touches the network, which keeps the parser and the service fully testable offline:
 * tests inject a fake fetcher that returns fixture HTML.
 */
public interface ProductPageFetcher {

    FetchResult fetch(String url);

    record FetchResult(boolean ok, String html, String error) {
        public static FetchResult ok(String html) {
            return new FetchResult(true, html, null);
        }

        public static FetchResult error(String message) {
            return new FetchResult(false, null, message);
        }
    }
}
