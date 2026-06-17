package ai.budgetspace.pricewatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sprint 10.34 — the real {@link LivePriceProbe}: a raw HTTP GET (JDK {@link HttpClient}) of a single
 * public product page, then a <strong>deterministic</strong> read of the current price from the page's
 * JSON-LD {@code offers.price} (the same authoritative field the catalog was sourced from; for JYSK this
 * is the live/promo price). No JS rendering, no crawling, no anti-bot bypass — exactly one URL, http(s)
 * only, bounded size/timeouts. If nothing parses cleanly it returns empty so the re-check never invents a
 * price. A browser User-Agent is used (as in the sourcing passes) because some sites omit the JSON-LD for
 * a default UA; this only reads a page served to everyone, it does not defeat any protection.
 */
@Component
public class HttpLivePriceProbe implements LivePriceProbe {
    private static final Logger log = LoggerFactory.getLogger(HttpLivePriceProbe.class);
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final int MAX_BYTES = 2_000_000;
    private static final Pattern LD_JSON = Pattern.compile(
            "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Optional<BigDecimal> currentPrice(String productUrl, String retailer) {
        String html = fetch(productUrl);
        if (html == null) return Optional.empty();
        return jsonLdPrice(html);
    }

    private String fetch(String url) {
        if (url == null || url.isBlank()) return null;
        final URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (RuntimeException e) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || uri.getHost() == null) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "hr,en;q=0.8")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.debug("Live price probe: {} returned {}", url, response.statusCode());
                return null;
            }
            String body = response.body();
            if (body == null || body.isBlank()) return null;
            return body.length() > MAX_BYTES ? body.substring(0, MAX_BYTES) : body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.debug("Live price probe failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    /** Parse each JSON-LD block, find a Product's offers.price. Deterministic; empty when unsure. */
    private Optional<BigDecimal> jsonLdPrice(String html) {
        Matcher m = LD_JSON.matcher(html);
        while (m.find()) {
            JsonNode root;
            try {
                root = mapper.readTree(m.group(1).trim());
            } catch (Exception e) {
                continue;
            }
            Optional<BigDecimal> price = priceFromNode(root);
            if (price.isPresent()) return price;
        }
        return Optional.empty();
    }

    private Optional<BigDecimal> priceFromNode(JsonNode node) {
        if (node == null) return Optional.empty();
        if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<BigDecimal> p = priceFromNode(child);
                if (p.isPresent()) return p;
            }
            return Optional.empty();
        }
        if (!node.isObject()) return Optional.empty();
        if (isProduct(node) && node.has("offers")) {
            JsonNode offers = node.get("offers");
            JsonNode offer = offers.isArray() && offers.size() > 0 ? offers.get(0) : offers;
            if (offer != null) {
                JsonNode price = offer.has("price") ? offer.get("price") : offer.get("lowPrice");
                BigDecimal parsed = parsePrice(price);
                if (parsed != null && parsed.signum() > 0) return Optional.of(parsed);
            }
        }
        // Some sites wrap the Product inside @graph / mainEntity — recurse one level.
        for (String key : new String[]{"@graph", "mainEntity", "mainEntityOfPage"}) {
            if (node.has(key)) {
                Optional<BigDecimal> p = priceFromNode(node.get(key));
                if (p.isPresent()) return p;
            }
        }
        return Optional.empty();
    }

    private boolean isProduct(JsonNode node) {
        JsonNode type = node.get("@type");
        if (type == null) return false;
        if (type.isTextual()) return "Product".equalsIgnoreCase(type.asText());
        if (type.isArray()) {
            for (JsonNode t : type) {
                if (t.isTextual() && "Product".equalsIgnoreCase(t.asText())) return true;
            }
        }
        return false;
    }

    private BigDecimal parsePrice(JsonNode price) {
        if (price == null || price.isNull()) return null;
        try {
            String text = price.asText().trim().replace(",", ".");
            if (text.isEmpty()) return null;
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
