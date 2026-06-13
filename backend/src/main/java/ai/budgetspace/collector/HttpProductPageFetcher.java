package ai.budgetspace.collector;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Controlled HTTP fetcher built on the JDK {@link HttpClient} (no extra dependency, no
 * browser automation, no JS rendering). It is deliberately conservative:
 *
 * <ul>
 *   <li>connect + request timeouts,</li>
 *   <li>a plain identifying user-agent,</li>
 *   <li>bounded redirect following ({@link HttpClient.Redirect#NORMAL} — never infinite),</li>
 *   <li>http/https only,</li>
 *   <li>a response size cap so a huge page cannot exhaust memory.</li>
 * </ul>
 *
 * <p>It does not crawl, paginate or follow links found on the page — it fetches exactly the
 * one URL it is given.</p>
 */
@Component
public class HttpProductPageFetcher implements ProductPageFetcher {
    private static final String USER_AGENT = "BudgetSpaceCollector/0.1 (+dev; controlled product fetch)";
    private static final int MAX_BYTES = 2_000_000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public FetchResult fetch(String url) {
        final URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (RuntimeException exception) {
            return FetchResult.error("URL ne izgleda ispravno.");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || uri.getHost() == null) {
            return FetchResult.error("URL mora biti http(s) s ispravnom domenom.");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return FetchResult.error("Stranica je vratila status " + response.statusCode() + ".");
            }
            String body = response.body();
            if (body == null || body.isBlank()) {
                return FetchResult.error("Prazan odgovor sa stranice.");
            }
            if (body.length() > MAX_BYTES) {
                body = body.substring(0, MAX_BYTES);
            }
            return FetchResult.ok(body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return FetchResult.error("Dohvaćanje je prekinuto.");
        } catch (Exception exception) {
            return FetchResult.error("Stranica nije dostupna ili je predugo trajala.");
        }
    }
}
