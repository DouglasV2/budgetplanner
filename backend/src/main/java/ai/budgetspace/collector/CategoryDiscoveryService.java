package ai.budgetspace.collector;

import ai.budgetspace.dto.DiscoveryRequestDto;
import ai.budgetspace.dto.DiscoveryResponseDto;
import ai.budgetspace.product.ProductTaxonomy;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dev-only, controlled product-URL discovery (Sprint 10.x).
 *
 * <p>Fetches a <strong>single</strong> retailer listing/category page and returns the product
 * URLs found on it, so a developer can review them and hand a small list to the collector. It
 * is deliberately <strong>not</strong> a crawler: it reads exactly one page, never paginates,
 * never follows links, and never uses a browser. Results are capped and limited to the
 * retailer's own allowed domain.</p>
 */
@Service
public class CategoryDiscoveryService {
    private static final int MAX_URLS = 20;
    private static final Pattern HREF = Pattern.compile("(?is)href\\s*=\\s*[\"']([^\"'#]+)[\"']");

    // Allowed listing-page hosts per retailer. Product links must live on the same host.
    private static final Map<String, List<String>> RETAILER_DOMAINS = Map.of(
            "IKEA", List.of("ikea.com", "www.ikea.com", "ikea.hr", "www.ikea.hr", "example-ikea.com", "www.example-ikea.com"),
            "JYSK", List.of("jysk.com", "www.jysk.com", "jysk.hr", "www.jysk.hr", "example.com", "www.example.com")
    );

    // Path fragment that marks a product page for a retailer.
    private static final Map<String, String> PRODUCT_PATH = Map.of("IKEA", "/p/", "JYSK", "/p/");

    private final ProductPageFetcher fetcher;

    public CategoryDiscoveryService(ProductPageFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public DiscoveryResponseDto discover(DiscoveryRequestDto request) {
        List<String> warnings = new ArrayList<>();
        if (request == null || isBlank(request.listingUrl())) {
            return rejected(null, null, "Nedostaje listingUrl.");
        }
        Optional<String> retailerOpt = ProductTaxonomy.normalizeRetailer(request.retailer());
        if (retailerOpt.isEmpty()) {
            return rejected(request.retailer(), request.listingUrl(), "Trgovina nije podržana: " + request.retailer() + ".");
        }
        String retailer = retailerOpt.get();
        String listingUrl = request.listingUrl().trim();

        URI listingUri = parseHttpUri(listingUrl);
        if (listingUri == null) {
            return rejected(retailer, listingUrl, "listingUrl mora biti ispravan http(s) URL.");
        }
        if (!isAllowedHost(listingUri.getHost(), retailer)) {
            return rejected(retailer, listingUrl, "Domena nije podržana za " + retailer + ".");
        }

        ProductPageFetcher.FetchResult result = fetcher.fetch(listingUrl);
        if (!result.ok()) {
            return rejected(retailer, listingUrl, result.error());
        }

        int cap = request.maxUrls() == null || request.maxUrls() <= 0 ? MAX_URLS : Math.min(request.maxUrls(), MAX_URLS);
        String productMarker = PRODUCT_PATH.getOrDefault(retailer, "/p/");
        Set<String> productUrls = new LinkedHashSet<>();

        Matcher matcher = HREF.matcher(result.html());
        while (matcher.find() && productUrls.size() < cap) {
            String absolute = toAbsolute(matcher.group(1).trim(), listingUri);
            if (absolute == null) continue;
            URI uri = parseHttpUri(absolute);
            if (uri == null || uri.getHost() == null) continue;
            if (!sameHost(uri.getHost(), listingUri.getHost())) continue;
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.toLowerCase(Locale.ROOT).contains(productMarker)) {
                productUrls.add(stripFragment(absolute));
            }
        }

        if (productUrls.isEmpty()) {
            warnings.add("Na stranici nisu pronađeni product linkovi — stranica je možda JS-heavy ili promijenjenog layouta. Probaj drugu listing stranicu ili ručno odaberi URL-ove.");
        }
        String message = "Pronađeno " + productUrls.size() + " product URL-ova na jednoj stranici.";
        return new DiscoveryResponseDto(retailer, listingUrl, productUrls.size(), new ArrayList<>(productUrls), message, warnings);
    }

    private DiscoveryResponseDto rejected(String retailer, String listingUrl, String message) {
        return new DiscoveryResponseDto(retailer, listingUrl, 0, List.of(), message, List.of());
    }

    private URI parseHttpUri(String url) {
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return null;
            return uri.getHost() == null ? null : uri;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String toAbsolute(String href, URI base) {
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        if (href.startsWith("//")) return base.getScheme() + ":" + href;
        if (href.startsWith("/")) return base.getScheme() + "://" + base.getHost() + href;
        return null; // ignore relative paths without a leading slash and other schemes
    }

    private String stripFragment(String url) {
        int hash = url.indexOf('#');
        return hash >= 0 ? url.substring(0, hash) : url;
    }

    private boolean isAllowedHost(String host, String retailer) {
        if (host == null) return false;
        List<String> allowed = RETAILER_DOMAINS.get(retailer);
        if (allowed == null) return false;
        String hostLower = host.toLowerCase(Locale.ROOT);
        return allowed.stream().anyMatch(allowedHost -> sameHost(hostLower, allowedHost));
    }

    private boolean sameHost(String host, String other) {
        if (host == null || other == null) return false;
        String a = host.toLowerCase(Locale.ROOT);
        String b = other.toLowerCase(Locale.ROOT);
        return a.equals(b) || a.endsWith("." + b) || b.endsWith("." + a);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
