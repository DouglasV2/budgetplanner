# Design — colour coherence in plans (A: reliable colour data, B: planner coordination)

Date: 2026-07-11. Branch: `sprint-10.178-catalog-expansion`.
Trigger: a bathroom plan mixed a **black toilet with a white washbasin**. Two root causes, both addressed here.

## Problem (evidence)

- The planner scores each product **independently** against the user's request (`scoreProduct`): style vs `input.style`,
  colour/material only vs the user's *stated* preferences (`colorMaterialBonus`). Nothing rewards a pick for matching
  the colours of the **other** items in the same plan → colours drift (black toilet + white basin when no colour stated).
- Colour data is unreliable: of **245 bathroom fixtures only 77 (32%) carry `colorTags`** (68% untagged — my IKEA
  washbasins + localized DE/FR/… names the current heuristic deriver misses), and there is **1 clearly-wrong tag**
  (`WC školjka … Rimless black` tagged `[white]`). So coordination has nothing reliable to coordinate on.
- `input.size` (m²) is **not** part of this design — separate concern, echoed only in the plan description today.

## A — reliable colour data (foundation)

`ProductTaxonomy.deriveColorTags` derives colour tags from a product name at **import time** when the snapshot row
carries none. Two fixes:

1. **Word-boundary + multilingual matching.** Today it does naive `haystack.contains(substr)` on an accent-stripped
   name → collisions (`blu` ∈ `blumen`). Change to match each colour's stems on a word boundary, and extend the
   vocabulary to the 15 market languages for the common colours (white/black/grey/beige/brown/green/blue/natural-oak):
   e.g. white += `weiss/blanc/bianco/blanco/branco/wit/valko/hvit/vit/hvid/biel/bela`; black += `schwarz/noir/nero/
   negro/preto/zwart/musta/svart/sort/cierna`; oak/natural += `eiche/chene/rovere/roble/carvalho/eik/tammi/dub/eken`;
   etc. Conservative — only high-confidence stems.

2. **No JSON churn for empty tags.** Because the improved deriver runs at import for any row without explicit
   `colorTags`, the 68% untagged fixtures auto-populate with accurate colours at seed time. **No catalog edit needed**
   for those.

3. **Fix explicit contradictions in JSON** (the deriver is bypassed when a row has explicit tags). Re-scan with the
   improved token matcher and correct the handful whose name unambiguously contradicts the stored tag — currently the
   1 black toilet (`real-pevex-hr-bathroom-10-169.json`: `["white"]` → `["black"]`). Never touch a tag that isn't a
   clear contradiction.

## B — coordination in the planner (only when the user states NO colour)

In `PlannerService.buildPlan` track a `currentColors` set (colours of items already picked this plan), threaded into
`scoreProduct` exactly like the existing `currentRetailers`. Add two small, capped bonuses in `scoreProduct`, applied
**only when `input.colorPreferences` is empty** (an explicit "crna kupaonica" keeps full control):

1. **Colour-coherence bonus** — reward a candidate whose `colorTags` overlap `currentColors` (the palette already
   forming). Cap ≈ **12** (well below styleScore 38 / roomScore 36 / big price biases) so it breaks ties toward a
   shared palette without overriding style or budget. General (all rooms), but most visible in bathroom fixtures.

2. **Neutral anchor for fixtures** — for the core fixture categories (`toilet`/`washbasin`/`bath-shower`), a small
   bonus (≈ 8) for neutral colours (white / light-grey / beige / natural-oak) so the FIRST fixture (which seeds the
   palette) leans neutral → a coherent white/neutral bathroom by default instead of a random loud colour.

Total added colour influence stays < styleScore, so budget/style/room remain dominant; this is a nudge, not a rule.

## Testing

- `ProductTaxonomyTest`: `deriveColorTags` — multilingual hits (weiß→white, chêne→natural, musta→black), and
  **no collision** (`blumenförmig` ⇏ blue; `braun` ⇏ … only brown).
- `PlannerServiceTest` (or the 10.178 test): a no-colour bathroom plan is colour-coherent (fixtures don't mix e.g.
  white basin + black toilet); an explicit colour request still dominates (a "crna" request can pick black).
- Full backend suite (currently 375) must stay green; fix only genuine regressions.
- Live: restart backend (reseed with the new deriver), smoke an HR/DE bathroom plan → fixtures share a coherent palette.

## Risks / non-goals

- Re-derivation only **fills empty** tags + **fixes clear contradictions** → low churn. Improving the deriver may shift
  a few existing derived colours (generally more accurate); the full suite guards against regressions.
- Coordination is a soft nudge; where a name carries no colour signal the item stays uncoordinated (rare after A).
- Not in scope: true m² spatial fitting; a global palette optimiser; overriding budget/style for colour.
