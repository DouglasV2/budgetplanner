package ai.budgetspace.pricewatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.InetAddress;
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
 * price. Sends an honest, self-identifying User-Agent (matching {@code HttpProductPageFetcher}) — no browser
 * impersonation — per docs/sourcing-policy.md §1/§6; this only reads a page served to everyone.
 */
@Component
public class HttpLivePriceProbe implements LivePriceProbe {
    private static final Logger log = LoggerFactory.getLogger(HttpLivePriceProbe.class);
    // An honest, self-identifying User-Agent — NO browser impersonation (docs/sourcing-policy.md §1/§6), matching
    // the sibling HttpProductPageFetcher. We read only pages served to everyone; we do not forge a browser UA.
    static final String USER_AGENT = "BudgetSpaceCollector/0.1 (+https://budgetspace.ai; price re-check)";
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

    @Override
    public Liveness liveness(String productUrl, String retailer) {
        if (productUrl == null || productUrl.isBlank()) return Liveness.UNKNOWN;
        final URI uri;
        try {
            uri = URI.create(productUrl.trim());
        } catch (RuntimeException e) {
            return Liveness.UNKNOWN;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || uri.getHost() == null) {
            return Liveness.UNKNOWN;
        }
        if (isBlockedHost(uri)) return Liveness.UNKNOWN; // SSRF guard: never probe a private/loopback/link-local target
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "hr,en;q=0.8")
                    .GET()
                    .build();
            // We only need status + the final URL after redirects — discard the body (cheap, no 2MB buffer).
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            if (code == 404 || code == 410) return Liveness.DEAD;
            if (code == 403 || code == 429 || code >= 500) return Liveness.UNKNOWN; // blocked/transient — can't tell
            if (code == 200) return classifyLanding(uri, response.uri(), retailer);
            return Liveness.UNKNOWN;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Liveness.UNKNOWN;
        } catch (Exception e) {
            log.debug("Liveness probe failed for {}: {}", productUrl, e.getMessage());
            return Liveness.UNKNOWN;
        }
    }

    /**
     * Classify a 200 response by WHERE it landed after redirects (deterministic, conservative). Same page =
     * LIVE. IKEA keeps products under {@code /p/}; a bounce to {@code /cat/} (or anything without {@code /p/})
     * is a discontinued product = DEAD. For other retailers a preserved trailing product-id means a mere
     * re-slug (LIVE); a redirect up to a strict parent path or the site root is a category bounce = DEAD;
     * anything else is treated as LIVE (never retire on a guess). Package-private for direct unit testing.
     */
    static Liveness classifyLanding(URI requested, URI landed, String retailer) {
        if (landed == null) return Liveness.UNKNOWN;
        String reqHost = host(requested), finHost = host(landed);
        String reqPath = normPath(requested.getPath()), finPath = normPath(landed.getPath());
        if (reqHost.equals(finHost) && reqPath.equals(finPath)) return Liveness.LIVE;
        boolean ikea = (retailer != null && retailer.equalsIgnoreCase("IKEA")) || finHost.contains("ikea") || reqHost.contains("ikea");
        if (ikea) return finPath.contains("/p/") ? Liveness.LIVE : Liveness.DEAD;
        String reqId = trailingId(reqPath), finId = trailingId(finPath);
        if (reqId != null && reqId.equals(finId)) return Liveness.LIVE; // same product, re-slugged
        if (!finPath.isEmpty() && reqPath.startsWith(finPath) && finPath.length() < reqPath.length()) return Liveness.DEAD; // parent/category
        if (segments(finPath) <= 1) return Liveness.DEAD; // bounced to home / shallow landing
        return Liveness.LIVE;
    }

    private static String host(URI u) {
        String h = u.getHost() == null ? "" : u.getHost().toLowerCase();
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    private static String normPath(String p) {
        if (p == null || p.isEmpty()) return "";
        String s = p.toLowerCase();
        return s.length() > 1 && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static int segments(String path) {
        return (int) path.chars().filter(c -> c == '/').count();
    }

    private static final Pattern PRODUCT_ID = Pattern.compile("\\d{4,}");

    private static String trailingId(String path) {
        Matcher m = PRODUCT_ID.matcher(path);
        String last = null;
        while (m.find()) last = m.group();
        return last;
    }

    /**
     * SSRF guard: true if the URL's host resolves to a non-public address (loopback, link-local incl. the
     * 169.254.169.254 cloud-metadata IP, private RFC-1918 ranges, wildcard or multicast). The probe only ever
     * fetches curated catalog URLs, but this makes the "public retailer pages only" invariant hold in code even
     * if a bad URL ever reaches the DB. Unresolvable host → blocked (fail closed). Package-private for testing.
     */
    static boolean isBlockedHost(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) return true;
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                        || addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
                    return true;
                }
            }
            return false;
        } catch (Exception unresolvable) {
            return true;
        }
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
        if (isBlockedHost(uri)) return null; // SSRF guard: never fetch a private/loopback/link-local target
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
