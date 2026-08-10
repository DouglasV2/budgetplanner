package ai.budgetspace.product;

/**
 * Sprint 10.193 — what a SHIPPED catalog snapshot is allowed to contain.
 *
 * <p>Until now every catalog runtime test asserted that each row of its snapshot is planner-eligible. That
 * invariant held only because the snapshots were never re-verified after they were harvested. The 10.193
 * freshness sweep re-read all 21k product pages and marked the rows whose pages are gone
 * {@code availabilityStatus=unavailable}, exactly as {@link CatalogFreshnessService} does in production —
 * and a retired row is deliberately NOT planner-eligible ({@code ProductTaxonomy.canEnterPlanner} drops it
 * so no plan can ever link to a dead page).</p>
 *
 * <p>So the real invariant is "eligible unless retired": every shipped row is either usable by the planner
 * or explicitly marked gone. A row that is neither is still a bug and still fails.</p>
 */
final class ShippedRows {

    static boolean eligibleOrRetired(Product product) {
        return CatalogSourcePolicy.isPlannerEligible(product) || isRetired(product);
    }

    static boolean isRetired(Product product) {
        return product != null && "unavailable".equals(product.getAvailabilityStatus());
    }

    private ShippedRows() {
    }
}
