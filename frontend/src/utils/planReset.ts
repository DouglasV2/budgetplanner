// Sprint 10.186 (QoL): a shown plan belongs to the market it was built for (its catalog, currency and language).
// When the user switches country the plan is stale and should be cleared. It is stale only when a plan is shown,
// its market is known, and the active market has moved away from it — so a fresh visit (no plan), a plan opened
// in its own market, and a prompt-detected market switch during generation all correctly count as NOT stale.
export function isPlanStaleForMarket(activeMarket: string, planMarket: string | null, hasPlan: boolean): boolean {
  return hasPlan && planMarket !== null && activeMarket !== planMarket;
}
