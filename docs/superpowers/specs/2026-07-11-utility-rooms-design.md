# Utility rooms (garage / pantry / laundry / attic / basement) — design

**Status:** DESIGN, approved in direction 2026-07-11 (owner). Not yet implemented.
**Related:** builds on the same room-config engine as `studio`/`hallway` (see `PlannerService`
`CATEGORY_FLOW_BY_ROOM` / `CORE_CATEGORIES_BY_ROOM` / `COMFORT_CATEGORIES_BY_ROOM` /
`ROOM_LABELS` / `ROOM_CATALOG_TAGS` and `PlannerIntentExtractor` room keywords).

## Problem

The planner only recognizes a fixed room set (living-room, home-office, bedroom, home-gym, kitchen,
dining-room, hallway, bathroom, studio). **Any other room silently falls back to a living-room plan** —
so "garaža za 1000 €" returns a sofa + TV + rug (confirmed live: `roomType` unresolved →
`PlannerInputDto.normalized()` defaults blank → `living-room`). A colleague hit exactly this. Named
pieces in the prompt ("police, ormar i radni stol za garažu") are ignored because those categories are
not in the living-room flow. The app never says "I don't do garages" — it silently furnishes the wrong
room.

## Decision (owner, 2026-07-11)

Add real **utility rooms** — `garage`, `pantry`, `laundry`, `attic`, `basement` — furnished honestly from
the catalog we already have. **Chosen approach: A + "veseraj basket"** (config on the existing engine, no
catalog JSON churn, plus a deterministic touch so the laundry room reliably surfaces a real laundry
basket). `terrace`/`balcony` is **explicitly excluded** — the catalog has no outdoor furniture (77
incidental matches / ~9900 rows), so furnishing it would be dishonest.

### Grounding (why this is honest)

Live catalog category histogram (seeded 9939): `storage 1189`, `lighting 536`, `textiles 714`,
`desk 373`, plus real IKEA **laundry baskets** (`category:storage`, names "…za rublje / Wäsche /
laundry", €2.79–14.99, currently roomTagged bathroom from Sprint 10.177). These utility rooms are all
variants of one **storage/shelving + lighting archetype** drawn from that shared pool. What the catalog
does **not** have (and the plan must say so): washing machines/dryers, drying racks, garage
workbenches/tool chests/pegboards, gym gear, outdoor furniture.

## Design

### Voice & copy (hard rule)

Every user-facing string this feature adds — room labels, the coverage captions, any plan text, the
frontend picker labels — must read as **plain, human Croatian** (English for non-HR markets), in the
app's editorial voice (see `MEMORY.md` → *editorial-redesign-2026-07-09*, *palette-warm-interior*). **No
AI / marketing / "vibecoded" filler**, no robotic phrasing (no "Otključaj vrijednost", "Bez napora",
"Podignite svoj prostor", hedgy assistant-speak). Copy is hand-written and owner-reviewed, never
assembled from fragments to *sound* helpful. This is a standing app-wide preference, not just this
feature — existing robotic copy elsewhere in the app is to be removed on sight (tracked separately).

### The mechanism (no catalog changes)

`matchesRoom(product, roomType)` already draws from `ROOM_CATALOG_TAGS.getOrDefault(roomType,
List.of(roomType))` (`PlannerService` ~line 2038). So a new room reaches real products by mapping it to
the **existing** roomTags its products live under — exactly like `studio` → `[living-room, bedroom,
dining-room]`. `CATEGORY_FLOW_BY_ROOM` then limits *which* categories are picked. No product needs a new
tag except the laundry touch below.

### Per-room configuration (internal English key → HR label)

| key | label (`ROOM_LABELS`) | `CATEGORY_FLOW_BY_ROOM` (core → comfort → later) | `CORE_CATEGORIES_BY_ROOM` | `ROOM_CATALOG_TAGS` (pools) |
|---|---|---|---|---|
| `garage` | garaža | storage, desk, lighting, decor | storage | hallway, home-office, living-room, bedroom, kitchen |
| `pantry` | ostava | storage, kitchen-storage, lighting, decor | storage | kitchen, hallway, living-room, bedroom |
| `laundry` | praonica | storage, lighting, textiles, decor | storage | laundry, bathroom, hallway, kitchen, bedroom |
| `attic` | tavan | storage, lighting, decor | storage | hallway, living-room, bedroom, home-office |
| `basement` | podrum | storage, lighting, decor | storage | hallway, living-room, bedroom, home-office |

`COMFORT_CATEGORIES_BY_ROOM` = the flow minus the core (garage: {desk, lighting}; pantry:
{kitchen-storage, lighting}; laundry: {lighting, textiles}; attic/basement: {lighting}).
`desk` in the garage flow is the closest honest "workbench" the catalog offers (a sturdy table/desk).
Optional: `MOVE_IN_WEIGHTS` entries (~0.5 each, like hallway/bathroom) so whole-apartment ("Cijeli
stan") mode can include them; omitting them just leaves them out of move-in (safe).

### The "veseraj basket" touch

At import, derive a `laundry` roomTag on any product whose **name** matches laundry terms (multilingual,
word-boundary matched like Sprint 10.178 `deriveColorTags`): `za rublje | rublja | Wäsche | wasche |
laundry | bucato | colada | lessive | tvätt | vasketøj | perilo | pralni`. The basket stays
`category:storage` (so **no bathroom regression** — bathroom can still pick it, and no new frontend
category label is needed). The laundry room's single `storage` slot is then won by a real basket via a
small capped `scoreProduct` bonus (like the 10.178 coherence bonus, well below
styleScore(38)/roomScore(36)) that rewards a `laundry`-tagged item **only in the laundry room** (0
elsewhere), so a basket beats a generic shelf. (A second `storage` slot for a shelf too is an easy
refinement — deferred to the implementation plan.)

### Utility rooms are functional (refinement, implemented 2026-07-11)

The live smoke showed the value/stretch "spend-up" tiers dropping a €159 designer floor lamp into a garage
and a pricey bathroom mirror cabinet (over the €7 basket) into a laundry — even a €2000 bookcase 2× over a
garage's budget. Utility rooms are FUNCTIONAL, not splurge rooms, so `scoreProduct` gives every
`UTILITY_ROOMS = {garage, pantry, laundry, attic, basement}` a cheap-leaning price bias in EVERY tier (it
skips the spend-up target and the stretch pricey-wins bias). Result: the laundry basket wins its slot in
all three tiers; garages get a workbench + shelving + a plain work light; no utility plan blows the budget.
Side effect: a utility room's three tiers look nearly identical (there is no meaningful "premium garage") —
accepted as honest. This replaces the spec's earlier "small capped bonus alone" idea for the laundry slot
(a small bonus could not beat the spend-up); the `laundry` roomTag + the functional bias together do it.

### Honest coverage note

Thin rooms show one short, **hand-written** caption (a static `ROOM_COVERAGE_NOTE` map — NOT a templated /
machine-assembled sentence), in a plain human voice. Final wording is owner-approved. Draft:
- garage (garaža): „Police, radni stol i rasvjeta. Alat i sprave za vježbanje nemamo."
- laundry (praonica): „Košara za rublje, police, rasvjeta i tekstil. Perilice i sušilice nemamo."
- pantry / attic / basement: no caption (shelving + lighting cover them).

### Prompt detection (`PlannerIntentExtractor`)

Add room keyword matches (HR primary + a little EN/DE), mapped to the internal key, following the existing
`matches(text, "...")` pattern:
- `garage`: `garaž | garaza | radionic | workshop | garage`
- `pantry`: `ostav | špajz | spajz | smočnic | smocnic | pantry`
- `laundry`: `veseraj | praonic | perionic | rublj | laundry | wäsche | wasche`
- `attic`: `tavan | potkrovlj | attic | dachboden`
- `basement`: `podrum | basement | cellar | keller`

`terrace`/`balcony` is deliberately **not** mapped (stays an unrecognized room → today's living-room
default; see Out of scope).

### Frontend (optional section — trim in review if prompt-only is preferred)

Add the five rooms to the room selector (`markets.ts`/room list) with hr + en i18n labels (en:
garage, pantry, laundry room, attic, basement) so they are discoverable, not only reachable by typing.
Backend already accepts any `roomType` string, so this is additive.

## Files

- `backend/.../planner/PlannerService.java` — add the five rooms to `CATEGORY_FLOW_BY_ROOM`,
  `CORE_CATEGORIES_BY_ROOM`, `COMFORT_CATEGORIES_BY_ROOM`, `ROOM_LABELS`, `ROOM_CATALOG_TAGS`, optional
  `MOVE_IN_WEIGHTS`; add `ROOM_COVERAGE_NOTE` + wire into `describePlan`; add the laundry `scoreProduct`
  bonus.
- `backend/.../planner/PlannerIntentExtractor.java` — room keyword matches.
- `backend/.../product/ProductTaxonomy.java` (or the import derive path) — derive the `laundry` roomTag
  from the product name (multilingual), reusing the 10.178 word-boundary approach.
- `frontend/.../markets.ts` + `i18n.ts` (only if the frontend section is kept) — room list + labels.

## Tests (TDD)

- **Extractor** (`PlannerIntentExtractorTest`): each keyword + a synonym maps to the right internal key
  (garaža→garage, ostava→pantry, veseraj/praonica→laundry, tavan→attic, podrum→basement); `terasa` does
  NOT map to any utility room.
- **Laundry derive** (`ProductTaxonomyTest` or import test): a "…za rublje" / "…Wäsche" product gets the
  `laundry` roomTag; an unrelated product does not (no collision).
- **Planner** (`PlannerServiceTest`, synthetic products): each new room yields a **non-empty** plan of the
  right categories (garage → storage/desk, NOT sofa; pantry → storage; attic/basement → storage); the
  laundry room surfaces a `laundry`-tagged basket ahead of a generic shelf; the honest note is present for
  garage/laundry.
- **No regression:** existing rooms unchanged; full backend suite green (currently 387 incl. the size-m²
  work; this adds to it).
- **Live smoke:** reseed Docker backend, then `generate-fast` for garaža / ostava / veseraj / tavan /
  podrum (HR) → sensible non-living-room plans; confirm the room from logs.

## Acceptance

- "garaža za 1000 €" → shelving + workbench-desk + lighting (no sofa), honest note; budget respected.
- "veseraj / praonica za 500 €" → a real laundry basket + shelving + lighting + textiles, honest note.
- "ostava / špajz za 300 €" → pantry shelving + lighting.
- "tavan" / "podrum" → shelving + lighting.
- Every new room draws only real catalog products (never empty, never fabricated).
- Existing rooms and the full test suite unaffected.

## Out of scope

- **terrace / balcony / outdoor** — no outdoor furniture in the catalog; would be dishonest. Stays an
  unrecognized room (today's living-room default) until/unless outdoor stock is sourced.
- **Honest hint for still-unrecognized rooms** (a "nisam prepoznao X" message instead of a silent
  living-room default) — a separate, optional improvement; not in this spec.
- **Real utility stock** — washers/dryers/drying racks (laundry), workbenches/tool chests/pegboards
  (garage) — would need new sourcing; the honest note covers the gap for now.
- **home-gym re-enable** — separate concern (Sprint 10.79 de-scoped it; no verified gym products).
- The **size (m²) → piece-fit** feature (separate, already implemented + tested, awaiting commit).

## Context to read first

- `MEMORY.md` → *move-in-feature* (the "one engine, many doors" per-room config precedent) and
  *sprint-10-178-catalog-expansion* (the `deriveColorTags` word-boundary derive pattern the laundry
  derive copies).
- `PlannerService.matchesRoom` (~2038), `ROOM_CATALOG_TAGS` (~115), the room-config maps (~40–96),
  `PlannerIntentExtractor` room keywords (~135).
