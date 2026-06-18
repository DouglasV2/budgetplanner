package ai.budgetspace.geo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * Sprint 10.42 — geo-IP market hint. The frontend already guesses the market from the browser locale and
 * from the prompt text; this adds the visitor's <em>actual</em> country so e.g. a French visitor with an
 * English browser still starts on France.
 *
 * <p>We do NOT geolocate the IP ourselves (no third-party call, no IP stored): we read the 2-letter country
 * code that a CDN / reverse proxy in front of the app already computes and injects as a request header
 * (CloudFlare {@code CF-IPCountry}, AWS CloudFront, Vercel, Fastly, …). With no such header (local dev, or
 * no CDN) the endpoint returns {@code country=null} and the frontend simply falls back to its existing
 * browser-locale guess. Privacy-friendly and dependency-free; to activate in production, put the app behind
 * a CDN that sets a country header (or a proxy that injects one).</p>
 */
@RestController
public class GeoController {

    // Common CDN/proxy country headers, in priority order. First valid 2-letter code wins.
    private static final List<String> COUNTRY_HEADERS = List.of(
            "CF-IPCountry",              // CloudFlare
            "CloudFront-Viewer-Country", // AWS CloudFront
            "X-Vercel-IP-Country",       // Vercel
            "Fastly-Geo-Country",        // Fastly
            "X-Geo-Country",             // generic / custom proxy
            "X-Country"                  // generic / custom proxy
    );

    @GetMapping("/api/geo")
    public GeoDto geo(HttpServletRequest request) {
        for (String header : COUNTRY_HEADERS) {
            String value = request.getHeader(header);
            if (value == null) continue;
            String code = value.trim().toUpperCase(Locale.ROOT);
            // A real ISO-3166 alpha-2 country. CDNs use placeholders like "XX"/"T1" for unknown/Tor — we
            // return them as-is and let the frontend ignore anything that isn't one of our markets.
            if (code.matches("[A-Z]{2}")) {
                return new GeoDto(code, header);
            }
        }
        return new GeoDto(null, null);
    }
}
