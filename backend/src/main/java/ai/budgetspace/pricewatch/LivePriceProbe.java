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
}
