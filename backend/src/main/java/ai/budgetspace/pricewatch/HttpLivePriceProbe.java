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
        return jsonLdPrice(html, pathOf(productUrl));
    }

    private static String pathOf(String url) {
        try {
            String path = URI.create(url.trim()).getPath();
            return path == null || path.isBlank() ? null : path;
        } catch (RuntimeException e) {
            return null;
        }
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
        // Sprint 10.193: the landed URL still CARRIES the requested id, just with more after it (Bygghemma
        // re-slugs /p-175779 -> /p-175779-175783 when a variant id is appended). Same product -> LIVE.
        if (reqId != null && finPath.contains(reqId)) return Liveness.LIVE;
        // Both URLs carry a product id but they DIFFER -> the requested product is gone and the site bounced us to a
        // DIFFERENT product (e.g. Kwantum salontafel-4323413 -> eettafel-4320261) -> dead.
        if (reqId != null && finId != null) return Liveness.DEAD;
        // A parent/category bounce is genuinely SHALLOWER. A landed path that is merely a string prefix at the
        // same depth is a shortened slug (jysk.se …-natur-vildekfargat -> …-natur-vildek) — still the product.
        if (!finPath.isEmpty() && reqPath.startsWith(finPath + "/") && depth(finPath) < depth(reqPath)) {
            return Liveness.DEAD; // parent/category
        }
        // Only a true bounce to the site ROOT/home is dead. A same-depth single-segment RE-SLUG (e.g. a retailer
        // like Harvey Norman whose products live at /slug and one re-slugs /trosjed-filip -> /trosjed-filip-v2) must
        // NOT be retired — the old "<=1 segment" rule wrongly killed those live products. (Sprint 10.167 fix.)
        if (finPath.isEmpty() || finPath.equals("/")) return Liveness.DEAD; // bounced to home
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


    /** Path depth in segments — a real parent bounce loses at least one. */
    private static int depth(String path) {
        int segments = 0;
        for (String part : path.split("/")) {
            if (!part.isBlank()) segments++;
        }
        return segments;
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
                byte[] b = addr.getAddress();
                // CGNAT / RFC 6598 100.64.0.0/10 (internal load balancers, cloud NAT) — not flagged by isSiteLocal.
                if (b.length == 4 && (b[0] & 0xFF) == 100 && (b[1] & 0xFF) >= 64 && (b[1] & 0xFF) <= 127) return true;
                // IPv6 Unique-Local fc00::/7 (fc../fd.. — internal service mesh / IPv6 metadata).
                if (b.length == 16 && (b[0] & 0xFE) == 0xFC) return true;
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

    /**
     * Parse each JSON-LD block and find the page's offers.price. Deterministic; empty when unsure.
     *
     * <p>Two passes, because a listing-shaped page carries more than one price: first look for the VARIANT
     * whose offer URL is the page we asked for (a {@code ProductGroup} lists every size, and only one of them
     * is our row), then fall back to the first product-level offer. Package-private so the shapes can be
     * unit-tested without a network.</p>
     */
    Optional<BigDecimal> jsonLdPrice(String html, String wantPath) {
        if (wantPath != null && !wantPath.isBlank()) {
            Matcher exact = LD_JSON.matcher(html);
            while (exact.find()) {
                JsonNode root = readTree(exact.group(1));
                if (root == null) continue;
                Optional<BigDecimal> variant = variantPrice(root, wantPath, 0);
                if (variant.isPresent()) return variant;
            }
        }
        Matcher m = LD_JSON.matcher(html);
        while (m.find()) {
            JsonNode root = readTree(m.group(1));
            if (root == null) continue;
            Optional<BigDecimal> price = priceFromNode(root);
            if (price.isPresent()) return price;
        }
        return Optional.empty();
    }

    private JsonNode readTree(String raw) {
        try {
            return mapper.readTree(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** The offer whose URL ends with the requested path — i.e. the variant this very page is showing. */
    private Optional<BigDecimal> variantPrice(JsonNode node, String wantPath, int depth) {
        if (node == null || depth > 6) return Optional.empty();
        if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<BigDecimal> p = variantPrice(child, wantPath, depth + 1);
                if (p.isPresent()) return p;
            }
            return Optional.empty();
        }
        if (!node.isObject()) return Optional.empty();
        JsonNode variants = node.get("hasVariant");
        if (variants != null && variants.isArray()) {
            String want = stripTrailingSlash(wantPath);
            for (JsonNode variant : variants) {
                JsonNode offer = firstOffer(variant.get("offers"));
                String url = offer != null && offer.hasNonNull("url") ? offer.get("url").asText()
                        : variant.hasNonNull("url") ? variant.get("url").asText() : null;
                if (url != null && stripTrailingSlash(url).endsWith(want)) {
                    BigDecimal parsed = offerPrice(offer);
                    if (parsed != null) return Optional.of(parsed);
                }
            }
        }
        for (String key : new String[]{"@graph", "mainEntity", "mainEntityOfPage"}) {
            if (node.has(key)) {
                Optional<BigDecimal> p = variantPrice(node.get(key), wantPath, depth + 1);
                if (p.isPresent()) return p;
            }
        }
        return Optional.empty();
    }

    private static String stripTrailingSlash(String value) {
        return value.length() > 1 && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
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
            BigDecimal parsed = offerPrice(firstOffer(node.get("offers")));
            if (parsed != null) return Optional.of(parsed);
        }
        // Some sites wrap the Product inside @graph / mainEntity / hasVariant — recurse.
        for (String key : new String[]{"@graph", "mainEntity", "mainEntityOfPage", "hasVariant"}) {
            if (node.has(key)) {
                Optional<BigDecimal> p = priceFromNode(node.get(key));
                if (p.isPresent()) return p;
            }
        }
        return Optional.empty();
    }

    private static JsonNode firstOffer(JsonNode offers) {
        if (offers == null) return null;
        return offers.isArray() && offers.size() > 0 ? offers.get(0) : offers;
    }

    private BigDecimal offerPrice(JsonNode offer) {
        if (offer == null) return null;
        JsonNode price = offer.has("price") ? offer.get("price") : offer.get("lowPrice");
        // Some storefronts nest the number: price: { unformatted: { minSingle: 129.9 } }.
        if (price != null && price.isObject()) {
            JsonNode unformatted = price.get("unformatted");
            JsonNode nested = unformatted != null ? unformatted.get("minSingle") : null;
            price = nested != null ? nested : price.get("value");
        }
        BigDecimal parsed = parsePrice(price);
        return parsed != null && parsed.signum() > 0 ? parsed : null;
    }

    /**
     * A price-carrying node. {@code ProductGroup} counts: six JYSK storefronts (AT/DK/FI/FR/IT/PT) and several
     * Shopify shops publish the page's price ONLY under that type, so a Product-only reader saw no price at all
     * there and left every one of those rows permanently hedged as {@code check-store}.
     */
    private boolean isProduct(JsonNode node) {
        JsonNode type = node.get("@type");
        if (type == null) return false;
        if (type.isTextual()) return isProductType(type.asText());
        if (type.isArray()) {
            for (JsonNode t : type) {
                if (t.isTextual() && isProductType(t.asText())) return true;
            }
        }
        return false;
    }

    private static boolean isProductType(String type) {
        return "Product".equalsIgnoreCase(type) || "ProductGroup".equalsIgnoreCase(type);
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
