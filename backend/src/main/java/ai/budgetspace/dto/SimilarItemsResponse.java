package ai.budgetspace.dto;

// Sprint 10.173 (P0 — similar-item + budget-option discovery): up to three DISTINCT alternatives to the anchor,
// all from the current market's verified catalog and priced within the cap:
//   - budgetPick: the cheapest distinct option,
//   - bestValue:  the balanced score winner (the recommended pick),
//   - nicer:      a genuine step up (higher-rated, priced above the best-value pick).
// Any bucket is null when the verified catalog has nothing that fits — the UI shows what exists and NEVER
// fabricates a match, a price or a URL. `cap` echoes the (clamped) ceiling actually applied; `currency` is the
// market's currency so the UI can label the caps correctly.
public record SimilarItemsResponse(
        ProductDto budgetPick,
        ProductDto bestValue,
        ProductDto nicer,
        int cap,
        String currency
) {
    public static SimilarItemsResponse empty(int cap, String currency) {
        return new SimilarItemsResponse(null, null, null, cap, currency);
    }
}
