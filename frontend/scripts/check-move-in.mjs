// Sprint 10.183 (Move-In QoL): zero-dependency unit checks for the pure helpers in src/utils/moveInPlan.ts.
// The repo has no test runner (build is just `tsc -b && vite build`), so we load the TS helper directly via
// Node's built-in type-stripping (Node >= 22.6; unflagged on 23+) — the helper uses only erasable TS
// (interfaces + `import type` + `as` casts), so no transpiler dependency is needed. Run `npm run check:movein`.
import { pathToFileURL, fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import assert from 'node:assert/strict';

const here = dirname(fileURLToPath(import.meta.url));
let assertions = 0;
const check = (cond, msg) => { assertions += 1; assert.ok(cond, msg); };

const m = await import(pathToFileURL(join(here, '..', 'src', 'utils', 'moveInPlan.ts')).href);

// Fixtures ---------------------------------------------------------------------------------------------------
const item = (id, price, market = 'HR', quantity = 1, retailer = 'IKEA') =>
  ({ product: { id, price, market, retailer, name: id, category: 'sofa' }, quantity, reason: '' });
const room = (roomType, total, items, locked = []) => ({
  roomType, labelKey: '', allocatedBudget: total, hasItems: items.length > 0, partial: false,
  plan: { id: 'value', total, items, retailersUsed: [] }, input: { lockedProductIds: locked },
});

// --- hydrateSession: garbage / empty -> defaults ---
{
  const s = m.hydrateSession(null, 'HR');
  check(s.market === 'HR', 'empty session stamps current market');
  assert.deepEqual(s.rooms, ['living-room', 'bedroom']);
  check(s.budget === 5000, 'empty session default budget');
  check(s.results === null, 'empty session has no results');
  assert.deepEqual(s.purchasedIds, []);
  assert.deepEqual(s.priorities, {});
  assert.deepEqual(s.retainedRooms, []);
}

// --- hydrateSession: migrates the legacy {rooms,budget} draft (no market stamp) ---
{
  const s = m.hydrateSession({ rooms: ['kitchen', 'bathroom'], budget: 3200 }, 'HR');
  assert.deepEqual(s.rooms, ['kitchen', 'bathroom']);
  check(s.budget === 3200, 'legacy draft budget carried forward');
  check(s.results === null, 'legacy draft has no results');
}

// --- hydrateSession: market MISMATCH drops market-specific state, keeps market-agnostic ---
{
  const raw = {
    market: 'DE', rooms: ['kitchen'], budget: 3000,
    priorities: { kitchen: 'now' }, retainedRooms: ['kitchen'],
    results: [{ roomType: 'kitchen' }], purchasedIds: ['p1'],
  };
  const s = m.hydrateSession(raw, 'HR');
  check(s.market === 'HR', 'mismatch restamps to current market');
  assert.deepEqual(s.rooms, ['kitchen']);
  assert.deepEqual(s.priorities, { kitchen: 'now' });
  assert.deepEqual(s.retainedRooms, ['kitchen'], 'kept rooms are market-agnostic and survive');
  check(s.results === null, 'mismatch DROPS foreign-market results');
  assert.deepEqual(s.purchasedIds, [], 'mismatch drops purchased ids');
}

// --- hydrateSession: market MATCH keeps market-specific state ---
{
  const raw = { market: 'HR', rooms: ['living-room'], budget: 4000, results: [{ roomType: 'living-room' }], purchasedIds: ['p1', 'p2'] };
  const s = m.hydrateSession(raw, 'HR');
  check(Array.isArray(s.results) && s.results.length === 1, 'match keeps results');
  assert.deepEqual(s.purchasedIds, ['p1', 'p2']);
}

// --- hydrateSession: invalid priority values rejected; serialize round-trips ---
{
  const s = m.hydrateSession({ priorities: { kitchen: 'bogus', bedroom: 'now' } }, 'HR');
  assert.deepEqual(s.priorities, { bedroom: 'now' }, 'invalid priority level dropped');
  const round = m.hydrateSession(JSON.parse(m.serializeSession(s)), 'HR');
  assert.deepEqual(round.priorities, s.priorities, 'serialize round-trips priorities');
}

// --- retainedTotal: kept room counts in full; other rooms only their locked products ---
{
  const results = [
    room('living-room', 500, [item('a', 300), item('b', 200)]),
    room('bedroom', 400, [item('c', 400)], ['c']),
    room('kitchen', 600, [item('d', 250, 'HR', 2)], []), // not kept, no locks -> contributes 0
  ];
  check(m.retainedTotal(results, ['living-room']) === 900, 'kept room (500) + locked product in another room (400) = 900');
  check(m.retainedTotal(results, []) === 400, 'no kept rooms -> only the locked bedroom product (400)');
  check(m.retainedExceedsBudget(results, ['living-room'], 800) === true, '900 retained exceeds an 800 budget');
  check(m.retainedExceedsBudget(results, ['living-room'], 1000) === false, '900 retained fits a 1000 budget');
}

// --- retainedTotal: a locked product inside a kept room is not double-counted ---
{
  const results = [room('living-room', 500, [item('a', 300), item('b', 200)], ['a'])];
  check(m.retainedTotal(results, ['living-room']) === 500, 'kept room counts once (500), not 500+300');
}

// --- clearForeignMarketRetained: drops locked ids whose product is a different market ---
{
  const results = [room('bedroom', 300, [item('p1', 100, 'HR'), item('p2', 200, 'DE')], ['p1', 'p2'])];
  const out = m.clearForeignMarketRetained(results, 'HR');
  check(out.cleared === 1, 'one foreign-market lock cleared');
  assert.deepEqual(out.results[0].input.lockedProductIds, ['p1'], 'HR lock kept, DE lock dropped');
}
{
  const results = [room('bedroom', 300, [item('p1', 100, 'HR')], ['p1'])];
  const out = m.clearForeignMarketRetained(results, 'HR');
  check(out.cleared === 0 && out.results === results, 'no foreign locks -> same array back (no churn)');
}

// --- apartmentStatus: totals, covered/attention rooms, aggregated missing buckets ---
{
  const results = [
    { ...room('living-room', 500, [item('a', 300), item('b', 200)]), hasItems: true, partial: false,
      missingEssential: ['rug'], niceToHave: ['decor'], unavailableInMarket: [] },
    { ...room('bedroom', 400, [item('c', 400, 'HR', 1, 'JYSK')]), hasItems: true, partial: true,
      missingEssential: [], niceToHave: [], unavailableInMarket: ['wardrobe'] },
  ];
  const s = m.apartmentStatus(results, 1000);
  check(s.total === 900, 'status total = 900');
  check(s.remaining === 100, 'status remaining = budget - total');
  check(s.over === 0, 'status not over budget');
  check(s.roomCount === 2, 'status room count');
  check(s.retailerCount === 2, 'status distinct retailers (IKEA + JYSK)');
  assert.deepEqual(s.coveredRooms, ['living-room'], 'covered = has items & not partial');
  assert.deepEqual(s.attentionRooms, ['bedroom'], 'attention = partial/empty rooms');
  assert.deepEqual(s.missing.moveIn, ['rug'], 'aggregated essential-missing');
  assert.deepEqual(s.missing.niceToHave, ['decor'], 'aggregated nice-to-have');
  assert.deepEqual(s.missing.notFound, ['wardrobe'], 'aggregated market-unavailable');
}
{
  const s = m.apartmentStatus([{ ...room('living-room', 1200, [item('a', 1200)]), hasItems: true }], 1000);
  check(s.over === 200, 'over-budget = total - budget');
  check(s.remaining === -200, 'remaining goes negative when over');
}

// --- checklist: group by retailer, richest first; totals by purchased id ---
{
  const results = [
    room('living-room', 700, [item('a', 300, 'HR', 1, 'IKEA'), item('b', 200, 'HR', 2, 'IKEA')]),
    room('bedroom', 150, [item('c', 150, 'HR', 1, 'JYSK')]),
  ];
  const groups = m.checklist(results);
  check(groups.length === 2, 'two retailer groups');
  check(groups[0].retailer === 'IKEA', 'richest store first (IKEA 700)');
  check(groups[0].count === 3, 'IKEA count = 1 + 2 (quantity)');
  check(groups[0].total === 700, 'IKEA total = 300 + 400');
  check(groups[1].retailer === 'JYSK', 'second store JYSK');

  const totals = m.checklistTotals(results, ['a']);
  check(totals.bought === 300, 'bought = line total of purchased id a');
  check(totals.remaining === 550, 'remaining = 400 (b) + 150 (c)');
}

console.log(`check-move-in: OK (${assertions} assertions + deepEqual checks)`);
