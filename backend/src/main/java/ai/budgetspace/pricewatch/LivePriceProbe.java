package ai.budgetspace.pricewatch;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Sprint 10.34 — reads the <em>current</em> price off a live product page, deterministically (no model,
 * no fabrication). Returns {@link Optional#empty()} when the price cannot be confidently extracted, so the
 * re-check skips that watch rather than risk a false alert. This is the same extraction approach used to
 * source the catalog (raw HTTP + JSON-LD), exposed as a seam so tests can stub it (no network in tests).
 */
public interface LivePriceProbe {
    Optional<BigDecimal> currentPrice(String productUrl, String retailer);

    /**
     * Sprint 10.167 — coarse liveness of a product URL, so the rolling freshness re-check can AUTO-RETIRE a
     * genuinely dead link (discontinued product) instead of only ever hedging it {@code check-store}. Purely
     * deterministic and conservative: it reports {@link Liveness#DEAD} only on an unambiguous signal (HTTP
     * 404/410, or a redirect that lands on a category/parent page rather than the product), and
     * {@link Liveness#UNKNOWN} for anything it cannot be sure about (anti-bot 403, timeout, a moved-but-live
     * slug) — never fabricates a retirement. Default {@code UNKNOWN} keeps existing stubs/tests untouched.
     */
    default Liveness liveness(String productUrl, String retailer) {
        return Liveness.UNKNOWN;
    }

    /** LIVE = product page still resolves; DEAD = gone (404/410 or bounced to a category); UNKNOWN = can't tell. */
    enum Liveness { LIVE, DEAD, UNKNOWN }
}
