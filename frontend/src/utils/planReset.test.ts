// Sprint 10.186 (QoL): the "is the shown plan stale after a country switch?" decision. Kept as a pure function
// so the rule that guards the Planner's reset effect is tested independently of the (heavy) component.
import { describe, expect, it } from 'vitest';
import { isPlanStaleForMarket } from './planReset';

describe('isPlanStaleForMarket', () => {
  it('is not stale when no plan is shown (fresh visit)', () => {
    expect(isPlanStaleForMarket('DE', 'HR', false)).toBe(false);
    expect(isPlanStaleForMarket('DE', null, false)).toBe(false);
  });

  it("is not stale when the plan's market is unknown (avoid clearing a loaded plan with no market)", () => {
    expect(isPlanStaleForMarket('DE', null, true)).toBe(false);
  });

  it('is not stale while the active market still matches the plan (fresh generate / open-in-own-market)', () => {
    expect(isPlanStaleForMarket('HR', 'HR', true)).toBe(false);
    expect(isPlanStaleForMarket('DE', 'DE', true)).toBe(false);
  });

  it('is stale when a plan is shown and the country switched away from its market', () => {
    expect(isPlanStaleForMarket('DE', 'HR', true)).toBe(true);
    expect(isPlanStaleForMarket('GB', 'HR', true)).toBe(true);
  });
});
