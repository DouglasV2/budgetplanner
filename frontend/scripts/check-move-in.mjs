// Sprint 10.183 (Move-In QoL): zero-dependency unit checks for the pure helpers in src/utils/moveInPlan.ts.
// The repo has no test runner (build is just `tsc -b && vite build`), so we transpile the TS helper with the
// esbuild that already ships inside vite's dependency tree and assert against it with node:assert. Run via
// `npm run check:movein`.
import { transform } from 'esbuild';
import { readFileSync, writeFileSync, rmSync, mkdtempSync } from 'node:fs';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { tmpdir } from 'node:os';
import assert from 'node:assert/strict';

const here = dirname(fileURLToPath(import.meta.url));
let assertions = 0;
const check = (cond, msg) => { assertions += 1; assert.ok(cond, msg); };

async function loadTs(relPath) {
  const source = readFileSync(join(here, '..', relPath), 'utf8');
  const { code } = await transform(source, { loader: 'ts', format: 'esm', target: 'es2020' });
  const dir = mkdtempSync(join(tmpdir(), 'bs-check-'));
  const outfile = join(dir, 'mod.mjs');
  writeFileSync(outfile, code);
  try {
    return await import(pathToFileURL(outfile).href);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

const m = await loadTs('src/utils/moveInPlan.ts');

// --- hydrateSession: garbage / empty -> defaults ---
{
  const s = m.hydrateSession(null, 'HR');
  check(s.market === 'HR', 'empty session stamps current market');
  assert.deepEqual(s.rooms, ['living-room', 'bedroom']);
  check(s.budget === 5000, 'empty session default budget');
  check(s.results === null, 'empty session has no results');
  assert.deepEqual(s.purchasedIds, []);
  assert.deepEqual(s.priorities, {});
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
    results: [{ roomType: 'kitchen' }], purchasedIds: ['p1'], lockedByRoom: { kitchen: ['p9'] },
  };
  const s = m.hydrateSession(raw, 'HR');
  check(s.market === 'HR', 'mismatch restamps to current market');
  assert.deepEqual(s.rooms, ['kitchen']);
  check(s.budget === 3000, 'mismatch keeps budget');
  assert.deepEqual(s.priorities, { kitchen: 'now' });
  assert.deepEqual(s.retainedRooms, ['kitchen']);
  check(s.results === null, 'mismatch DROPS foreign-market results');
  assert.deepEqual(s.purchasedIds, [], 'mismatch drops purchased ids');
  assert.deepEqual(s.lockedByRoom, {}, 'mismatch drops locked ids');
}

// --- hydrateSession: market MATCH keeps market-specific state ---
{
  const raw = {
    market: 'HR', rooms: ['living-room'], budget: 4000,
    results: [{ roomType: 'living-room' }], purchasedIds: ['p1', 'p2'], lockedByRoom: { 'living-room': ['p3'] },
  };
  const s = m.hydrateSession(raw, 'HR');
  check(Array.isArray(s.results) && s.results.length === 1, 'match keeps results');
  assert.deepEqual(s.purchasedIds, ['p1', 'p2']);
  assert.deepEqual(s.lockedByRoom, { 'living-room': ['p3'] });
}

// --- hydrateSession: invalid priority values are rejected ---
{
  const s = m.hydrateSession({ priorities: { kitchen: 'bogus', bedroom: 'now' } }, 'HR');
  assert.deepEqual(s.priorities, { bedroom: 'now' }, 'invalid priority level dropped');
}

// --- serializeSession round-trips through hydrate ---
{
  const original = m.hydrateSession({ market: 'HR', rooms: ['bedroom'], budget: 2500, purchasedIds: ['x'] }, 'HR');
  const round = m.hydrateSession(JSON.parse(m.serializeSession(original)), 'HR');
  assert.deepEqual(round.rooms, original.rooms);
  assert.deepEqual(round.purchasedIds, original.purchasedIds);
  check(round.budget === original.budget, 'round-trip preserves budget');
}

console.log(`check-move-in: OK (${assertions} assertions + deepEqual checks)`);
