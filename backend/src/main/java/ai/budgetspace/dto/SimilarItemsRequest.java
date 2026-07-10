package ai.budgetspace.dto;

// Sprint 10.173 (P0 — similar-item + budget-option discovery): the request for "find similar items under my
// budget". `product` is the anchor row the user is looking at, `input` carries market/room/retailer/style
// context (same as a plan), and `budgetCap` is the chosen price ceiling (a quick cap or the remaining budget).
// Browse-only: this never mutates a plan.
public record SimilarItemsRequest(ProductDto product, PlannerInputDto input, int budgetCap) {
}
