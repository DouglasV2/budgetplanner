# Kitchen Complete-Mode (Increment 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a user asks for a *complete kitchen*, return real, priced, buyable modular kitchen **sets** (instead of today's "go to IKEA planner" dead-end), behind a deterministic kitchen-intent classifier and a new `kitchen-set` catalog category.

**Architecture:** A backend deterministic classifier routes kitchen prompts to `COMPLETE | COMPONENT | KITCHENWARE | NONE`; only `COMPLETE` acts this increment. On `COMPLETE`, `PlannerService` selects `kitchen-set` products for the market within budget and returns them in a new optional `completeKitchen` section of the existing `PlanGenerationResponse`. The frontend renders that section inline in `PlanResults` with an honest "modular, not fitted" note. Real modular sets are sourced (IKEA, DE/AT/HR) into a verified catalog JSON.

**Tech Stack:** Java 21 / Spring Boot (backend, JUnit 5 + Mockito + AssertJ), React 18 + TypeScript + Vite (frontend), Postgres catalog seeded from JSON.

## Global Constraints

- **No fabrication:** every sourced set needs a real product URL + price + verified image/source, or it does not ship. Never invent a set, price, or URL. (Same rule as all catalog work.)
- **Modular ≠ fitted:** complete-kitchen results are honestly labelled modular sets; keep the IKEA-planner link for true made-to-measure kitchens. Never imply a configured fitted-kitchen price.
- **Deterministic path is the one that runs:** AI is off by default (`BUDGETSPACE_AI_ENABLED=false`), so the classifier and builder MUST work rule-based, no LLM.
- **This increment = complete modular SETS only.** No components (cabinets/appliances/sink/worktops), no bundled totals, no kitchenware. Only the `kitchen-set` category is added to the taxonomy.
- **Sourcing markets:** DE + AT and HR first. IKEA-only this increment.
- **Backend build/test on this machine:** `$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; & "C:\Users\bpusic\maven-tmp\apache-maven-3.9.9\bin\mvn.cmd" -s "C:\Users\bpusic\.m2\settings-central.xml" -f backend\pom.xml -Dtest=<Class> test`. Frontend: `cd frontend && npm run build` (tsc + vite). Browser-verify only on port **5173** (`frontend-preview`) or 5180 — backend CORS blocks 5207.
- **Diacritics:** classifier normalizes NFD + strips combining marks before matching, so patterns are plain ASCII (mirror `PlanResults.tsx:42`).

---

## File Structure

**Backend (create):**
- `backend/src/main/java/ai/budgetspace/planner/KitchenIntentClassifier.java` — rule-based classifier + attribute parse. Returns `KitchenBrief(intent, shape, includeAppliances)`.
- `backend/src/main/java/ai/budgetspace/dto/CompleteKitchenDto.java` — the response section (sets + parsed attributes + note flag).
- `backend/src/test/java/ai/budgetspace/planner/KitchenIntentClassifierTest.java`

**Backend (modify):**
- `backend/src/main/java/ai/budgetspace/product/ProductTaxonomy.java` — add `kitchen-set` to `KNOWN_CATEGORIES` + aliases.
- `backend/src/main/java/ai/budgetspace/dto/PlanGenerationResponse.java` — add optional `completeKitchen` field + `withCompleteKitchen(...)`.
- `backend/src/main/java/ai/budgetspace/planner/PlannerService.java` — `buildCompleteKitchen(...)`; branch in the generate flow when intent = COMPLETE.
- `backend/src/test/java/ai/budgetspace/planner/PlannerServiceTest.java` — complete-kitchen selection tests.
- `backend/src/main/resources/catalog/real-ikea-kitchen-sets-10-175.json` — sourced sets (Task 7).
- `backend/src/main/java/ai/budgetspace/product/RealCatalogSeeder.java` — register the new JSON (Task 7).

**Frontend (modify):**
- `frontend/src/types/index.ts` — `ProductCategory` += `'kitchen-set'`; add `CompleteKitchen` interface.
- `frontend/src/api/client.ts` — `PlanGenerationResponse.completeKitchen?`.
- `frontend/src/utils/planner.ts` — `categoryLabels['kitchen-set']`.
- `frontend/src/components/PlanResults.tsx` — inline "Kompletna kuhinja" section + `FALLBACK_IMAGES['kitchen-set']`.
- `frontend/src/components/Planner.tsx` — analytics for complete-kitchen view + set click.
- `frontend/src/i18n.ts` + `frontend/src/messages/*.json` — new `kitchen.*` keys.
- `frontend/src/styles.css` — section styles.

---

## Task 1: Add the `kitchen-set` taxonomy category

**Files:**
- Modify: `backend/src/main/java/ai/budgetspace/product/ProductTaxonomy.java` (KNOWN_CATEGORIES ~line 145, CATEGORY_ALIASES ~line 321)
- Modify: `frontend/src/types/index.ts` (ProductCategory union, ~line 82-106)
- Modify: `frontend/src/utils/planner.ts` (categoryLabels map)
- Modify: `frontend/src/components/PlanResults.tsx` (FALLBACK_IMAGES map, ~line 148)
- Test: `backend/src/test/java/ai/budgetspace/product/` (find the existing ProductTaxonomy test; if none, assert via a small new test)

**Interfaces:**
- Produces: the canonical category string `"kitchen-set"`, valid in `ProductTaxonomy.isKnownCategory("kitchen-set")` and in the frontend `ProductCategory` type.

- [ ] **Step 1: Write the failing test.** Find the taxonomy test (`grep -rl "KNOWN_CATEGORIES\|isKnownCategory\|canonicalCategory" backend/src/test`). Add:

```java
@Test
void kitchenSetIsAKnownCategory() {
    assertThat(ProductTaxonomy.isKnownCategory("kitchen-set")).isTrue();
    assertThat(ProductTaxonomy.canonicalCategory("modular-kitchen")).isEqualTo("kitchen-set");
}
```
(If the class exposes different method names, mirror them — check `ProductTaxonomy.java` for the actual public API; `isKnownCategory`/`canonicalCategory` are the expected names, adjust to what exists.)

- [ ] **Step 2: Run it, verify it fails.**
Run the backend test command with `-Dtest=ProductTaxonomyTest` (or the real test class name).
Expected: FAIL (`kitchen-set` unknown).

- [ ] **Step 3: Implement.** In `ProductTaxonomy.java` add `"kitchen-set"` to the `KNOWN_CATEGORIES` set, and to `CATEGORY_ALIASES` map the synonyms:

```java
// in KNOWN_CATEGORIES (kitchen group)
"kitchen-set",
// in CATEGORY_ALIASES
Map.entry("modular-kitchen", "kitchen-set"),
Map.entry("complete-kitchen", "kitchen-set"),
Map.entry("kitchen-package", "kitchen-set"),
```

- [ ] **Step 4: Run it, verify it passes.** Expected: PASS.

- [ ] **Step 5: Frontend wiring.** In `frontend/src/types/index.ts` add `| 'kitchen-set'` to the `ProductCategory` union. In `frontend/src/utils/planner.ts` add to `categoryLabels`: `'kitchen-set': /* hr */ 'Kompletna kuhinja'` (match the map's shape — it may be keyed by locale; follow the existing entries). In `frontend/src/components/PlanResults.tsx` add to `FALLBACK_IMAGES`: `'kitchen-set': 'https://images.unsplash.com/photo-1556909212-d5b604d0c90d?auto=format&fit=crop&w=240&q=70',` (a generic kitchen photo — placeholder only, real sets are imageVerified).

- [ ] **Step 6: Verify frontend compiles.** Run `cd frontend && npm run build`. Expected: tsc clean (exhaustive `ProductCategory` maps now include `kitchen-set`).

- [ ] **Step 7: Commit.**
```bash
git add backend/src/main/java/ai/budgetspace/product/ProductTaxonomy.java backend/src/test frontend/src/types/index.ts frontend/src/utils/planner.ts frontend/src/components/PlanResults.tsx
git commit -m "feat(kitchen): add kitchen-set taxonomy category"
```

---

## Task 2: Kitchen intent classifier

**Files:**
- Create: `backend/src/main/java/ai/budgetspace/planner/KitchenIntentClassifier.java`
- Test: `backend/src/test/java/ai/budgetspace/planner/KitchenIntentClassifierTest.java`

**Interfaces:**
- Produces:
  - `enum KitchenIntent { COMPLETE, COMPONENT, KITCHENWARE, NONE }`
  - `enum KitchenShape { SINGLE_WALL, L_SHAPED, U_SHAPED, GALLEY, ISLAND, UNKNOWN }`
  - `record KitchenBrief(KitchenIntent intent, KitchenShape shape, boolean includeAppliances)`
  - `KitchenBrief classify(String prompt)` (static or instance).

- [ ] **Step 1: Write the failing tests.**

```java
package ai.budgetspace.planner;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import ai.budgetspace.planner.KitchenIntentClassifier.KitchenIntent;
import ai.budgetspace.planner.KitchenIntentClassifier.KitchenShape;

class KitchenIntentClassifierTest {
    private final KitchenIntentClassifier c = new KitchenIntentClassifier();

    @Test void detectsCompleteKitchenAcrossLanguages() {
        assertThat(c.classify("trebam kompletnu kuhinju do 3000 eura").intent()).isEqualTo(KitchenIntent.COMPLETE);
        assertThat(c.classify("ich brauche eine komplette Einbauküche").intent()).isEqualTo(KitchenIntent.COMPLETE);
        assertThat(c.classify("i want a complete modular kitchen").intent()).isEqualTo(KitchenIntent.COMPLETE);
    }
    @Test void detectsComponentIntent() {
        assertThat(c.classify("treba mi perilica posuđa").intent()).isEqualTo(KitchenIntent.COMPONENT);
        assertThat(c.classify("suche einen Backofen").intent()).isEqualTo(KitchenIntent.COMPONENT);
    }
    @Test void detectsKitchenwareIntent() {
        assertThat(c.classify("trebam tanjure i čaše").intent()).isEqualTo(KitchenIntent.KITCHENWARE);
    }
    @Test void nonKitchenPromptIsNone() {
        assertThat(c.classify("dnevni boravak s kaučem i tepihom").intent()).isEqualTo(KitchenIntent.NONE);
        assertThat(c.classify("asdfghjkl").intent()).isEqualTo(KitchenIntent.NONE);
    }
    @Test void parsesShapeAndAppliances() {
        var b = c.classify("kompletna L kuhinja s uređajima");
        assertThat(b.intent()).isEqualTo(KitchenIntent.COMPLETE);
        assertThat(b.shape()).isEqualTo(KitchenShape.L_SHAPED);
        assertThat(b.includeAppliances()).isTrue();
        assertThat(c.classify("komplette Küche ohne Geräte").includeAppliances()).isFalse();
    }
    @Test void completeWinsOverComponentWhenBoth() {
        // "cijela kuhinja s pećnicom" — asking for a whole kitchen that mentions an oven → COMPLETE, not COMPONENT.
        assertThat(c.classify("cijela kuhinja s pećnicom").intent()).isEqualTo(KitchenIntent.COMPLETE);
    }
}
```

- [ ] **Step 2: Run, verify fail.** `-Dtest=KitchenIntentClassifierTest` → FAIL (class missing).

- [ ] **Step 3: Implement.**

```java
package ai.budgetspace.planner;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic, multilingual kitchen-intent classifier (Increment 1). AI is off by default, so this rule-based
 * path is the one that runs. COMPLETE = the user wants a whole kitchen; COMPONENT = an individual part;
 * KITCHENWARE = pots/plates/etc (secondary); NONE = not a kitchen ask. Only COMPLETE is acted on this increment.
 * Diacritics are stripped before matching (mirrors PlanResults.isFittedKitchenIntent), so patterns are ASCII.
 */
public class KitchenIntentClassifier {
    public enum KitchenIntent { COMPLETE, COMPONENT, KITCHENWARE, NONE }
    public enum KitchenShape { SINGLE_WALL, L_SHAPED, U_SHAPED, GALLEY, ISLAND, UNKNOWN }
    public record KitchenBrief(KitchenIntent intent, KitchenShape shape, boolean includeAppliances) {}

    // COMPLETE: whole-kitchen phrasing. HR/DE/EN + a few obvious cognates. "kuhinj|kuchen|kitchen|cucina|cocina|
    // keuken..." near a "whole/complete/fitted/modular/renovate/furnish" word.
    private static final Pattern COMPLETE = Pattern.compile(
        "(kompletn\\w* kuhinj|cijel\\w* kuhinj|oprem\\w* kuhinj|uredi\\w* kuhinj|kuhinj\\w* u paketu|modularn\\w* kuhinj"
        + "|komplett\\w* kuch|einbaukuch|kuchenzeile|modulkuch"
        + "|complete kitchen|full kitchen|fitted kitchen|modular kitchen|kitchen renovation|furnish\\w* kitchen"
        + "|knoxhult|metod|enhet kuch|enhet kitchen)");

    // COMPONENT: an individual kitchen part.
    private static final Pattern COMPONENT = Pattern.compile(
        "(perilic\\w* posud|sudoper|slavin|pecnic|ploc\\w* za kuhanj|napa|hladnjak|zamrziva|ormaric|ladic|radn\\w* ploc"
        + "|geschirrspul|backofen|kochfeld|dunstabzug|kuhlschrank|spule|arbeitsplatt|schrank"
        + "|dishwasher|oven|hob|cooktop|extractor hood|fridge|refrigerator|freezer|sink|faucet|worktop|countertop|cabinet)");

    // KITCHENWARE: pots/plates/etc. Secondary — never auto-injected, but recognised for routing.
    private static final Pattern KITCHENWARE = Pattern.compile(
        "(posud|tanjur|zdjel|pribor za jelo|case|salic|lonac|tava|cookware|tableware|cutlery|plates|bowls|glasses|mugs|utensil)");

    // A word that says "this is about a kitchen at all" (guards KITCHENWARE/COMPONENT from firing on non-kitchen text).
    private static final Pattern KITCHEN_WORD = Pattern.compile("(kuhinj|kuchen|kuche|kitchen|cucina|cocina|keuken)");

    private static final Pattern APPLIANCES_YES = Pattern.compile("(s uredaj|sa uredaj|s aparat|mit gerat|with appliance|incl\\w* appliance)");
    private static final Pattern APPLIANCES_NO = Pattern.compile("(bez uredaj|bez aparat|ohne gerat|without appliance|no appliance)");

    public KitchenBrief classify(String prompt) {
        String p = normalize(prompt);
        KitchenIntent intent;
        if (COMPLETE.matcher(p).find()) {
            intent = KitchenIntent.COMPLETE;                 // whole-kitchen phrasing wins even if a part is named
        } else if (COMPONENT.matcher(p).find()) {
            intent = KitchenIntent.COMPONENT;
        } else if (KITCHENWARE.matcher(p).find() && KITCHEN_WORD.matcher(p).find()) {
            intent = KitchenIntent.KITCHENWARE;
        } else {
            intent = KitchenIntent.NONE;
        }
        return new KitchenBrief(intent, parseShape(p), parseAppliances(p));
    }

    private KitchenShape parseShape(String p) {
        if (Pattern.compile("(\\bu[ -]?kuhinj|u[ -]?shaped|u[ -]?form)").matcher(p).find()) return KitchenShape.U_SHAPED;
        if (Pattern.compile("(\\bl[ -]?kuhinj|l[ -]?shaped|l[ -]?form|kutn\\w* kuhinj)").matcher(p).find()) return KitchenShape.L_SHAPED;
        if (Pattern.compile("(otok|island|kochinsel)").matcher(p).find()) return KitchenShape.ISLAND;
        if (Pattern.compile("(galley|kuhinj\\w* u nizu|u dva reda|zeile)").matcher(p).find()) return KitchenShape.GALLEY;
        if (Pattern.compile("(jednoredn|single[ -]?wall|einzeilig|uz zid)").matcher(p).find()) return KitchenShape.SINGLE_WALL;
        return KitchenShape.UNKNOWN;
    }

    private boolean parseAppliances(String p) {
        if (APPLIANCES_NO.matcher(p).find()) return false;
        return APPLIANCES_YES.matcher(p).find();   // default false unless the user asks for appliances
    }

    private String normalize(String prompt) {
        if (prompt == null) return "";
        return java.text.Normalizer.normalize(prompt, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Step 4: Run, verify pass.** `-Dtest=KitchenIntentClassifierTest` → all PASS. If a keyword test fails, extend the relevant pattern (keywords are data, not logic — add the missing token) and re-run.

- [ ] **Step 5: Commit.**
```bash
git add backend/src/main/java/ai/budgetspace/planner/KitchenIntentClassifier.java backend/src/test/java/ai/budgetspace/planner/KitchenIntentClassifierTest.java
git commit -m "feat(kitchen): deterministic kitchen intent + attribute classifier"
```

---

## Task 3: `CompleteKitchenDto` + response wiring

**Files:**
- Create: `backend/src/main/java/ai/budgetspace/dto/CompleteKitchenDto.java`
- Modify: `backend/src/main/java/ai/budgetspace/dto/PlanGenerationResponse.java`

**Interfaces:**
- Produces:
  - `record CompleteKitchenDto(List<ProductDto> sets, String shape, boolean includeAppliances, boolean showModularNote)`
  - `PlanGenerationResponse.completeKitchen()` accessor + `PlanGenerationResponse withCompleteKitchen(CompleteKitchenDto ck)`.

- [ ] **Step 1: Read `PlanGenerationResponse.java`** to see whether it is a record or class and how existing `withAnalysis(...)`/`withCompleteKitchen`-style copy methods are written (mirror that exact style).

- [ ] **Step 2: Create the DTO.**
```java
package ai.budgetspace.dto;

import java.util.List;

// Sprint 10.175 (kitchen Increment 1): the "complete kitchen" result section. `sets` are real modular kitchen
// sets (each a ProductDto, category kitchen-set); shape/includeAppliances are what we understood (display only);
// showModularNote drives the honest "modular, not fitted" note. Empty `sets` => an honest "no set fits" state.
public record CompleteKitchenDto(List<ProductDto> sets, String shape, boolean includeAppliances, boolean showModularNote) {
    public static CompleteKitchenDto none() { return new CompleteKitchenDto(List.of(), "unknown", false, false); }
}
```

- [ ] **Step 3: Add the field to `PlanGenerationResponse`** as an optional component + a `withCompleteKitchen` copy method that mirrors `withAnalysis`. (If it's a record, add `CompleteKitchenDto completeKitchen` as the last component with a compact copy method; keep existing constructors/factory calls compiling — add an overload or default to `null` where it's built.)

- [ ] **Step 4: Compile.** Run backend `-Dtest=PlannerServiceTest` (it constructs responses) to confirm nothing broke. Expected: PASS (or only the not-yet-written Task 4 tests failing — those don't exist yet, so PASS).

- [ ] **Step 5: Commit.**
```bash
git add backend/src/main/java/ai/budgetspace/dto/CompleteKitchenDto.java backend/src/main/java/ai/budgetspace/dto/PlanGenerationResponse.java
git commit -m "feat(kitchen): CompleteKitchenDto on the plan response"
```

---

## Task 4: `PlannerService.buildCompleteKitchen` + branch on COMPLETE

**Files:**
- Modify: `backend/src/main/java/ai/budgetspace/planner/PlannerService.java`
- Test: `backend/src/test/java/ai/budgetspace/planner/PlannerServiceTest.java`

**Interfaces:**
- Consumes: `KitchenIntentClassifier.classify` (Task 2), `CompleteKitchenDto` (Task 3), existing `marketCatalog`, `ProductTaxonomy.canEnterPlanner`, `selectedRetailers`, `scoreProduct`, `ProductDto.from`.
- Produces: `CompleteKitchenDto buildCompleteKitchen(PlannerInputDto input, KitchenBrief brief)`; the generate flow attaches it via `response.withCompleteKitchen(...)` when intent = COMPLETE.

- [ ] **Step 1: Write failing tests** in `PlannerServiceTest.java` (reuse the existing `product(...)` / `serviceWithProducts(...)` / `input(...)` helpers; a set is just a product with category `kitchen-set`):

```java
@Test
void completeKitchenReturnsSetsWithinBudgetRankedAndMarketScoped() {
    Product setCheap = product("set-1", "KNOXHULT kuhinja 220cm", "IKEA", "kitchen-set", 900, 4.4);
    Product setMid   = product("set-2", "ENHET kuhinja 243cm", "IKEA", "kitchen-set", 1600, 4.7);
    Product setOver  = product("set-3", "Velika kuhinja 340cm", "IKEA", "kitchen-set", 5200, 4.8);
    Product sofa     = product("sofa-1", "Kauč", "IKEA", "sofa", 600, 4.5); // must NOT appear
    for (Product s : List.of(setCheap, setMid, setOver, sofa)) s.setRoomTags("kitchen");
    PlannerService service = serviceWithProducts(List.of(setCheap, setMid, setOver, sofa));

    var brief = new KitchenIntentClassifier.KitchenBrief(
        KitchenIntentClassifier.KitchenIntent.COMPLETE,
        KitchenIntentClassifier.KitchenShape.L_SHAPED, true);
    // budget 2000 (input helper default is 1500 → use a with-budget input)
    CompleteKitchenDto ck = service.buildCompleteKitchen(input("kompletna kuhinja").withBudget(2000), brief);

    List<String> ids = ck.sets().stream().map(ProductDto::id).toList();
    assertThat(ids).contains("set-1", "set-2");   // both under 2000
    assertThat(ids).doesNotContain("set-3", "sofa-1"); // over budget / not a set
    assertThat(ck.showModularNote()).isTrue();
    assertThat(ck.shape()).isEqualTo("l-shaped");
    assertThat(ck.includeAppliances()).isTrue();
}

@Test
void completeKitchenIsHonestlyEmptyWhenNothingFits() {
    Product setOver = product("set-1", "Skupa kuhinja", "IKEA", "kitchen-set", 5000, 4.8);
    setOver.setRoomTags("kitchen");
    PlannerService service = serviceWithProducts(List.of(setOver));
    var brief = new KitchenIntentClassifier.KitchenBrief(
        KitchenIntentClassifier.KitchenIntent.COMPLETE, KitchenIntentClassifier.KitchenShape.UNKNOWN, false);
    CompleteKitchenDto ck = service.buildCompleteKitchen(input("kompletna kuhinja").withBudget(2000), brief);
    assertThat(ck.sets()).isEmpty();
    assertThat(ck.showModularNote()).isTrue(); // note still shown so the user gets the honest framing
}

@Test
void generateAttachesCompleteKitchenOnCompletePrompt() {
    Product set = product("set-1", "KNOXHULT kuhinja", "IKEA", "kitchen-set", 900, 4.5);
    set.setRoomTags("kitchen");
    PlannerService service = serviceWithProducts(List.of(set));
    PlanGenerationResponse res = service.generate(input("trebam kompletnu kuhinju do 2000 eura").withBudget(2000));
    assertThat(res.completeKitchen()).isNotNull();
    assertThat(res.completeKitchen().sets()).extracting(ProductDto::id).contains("set-1");
}

@Test
void generateDoesNotAttachCompleteKitchenOnAPlainRoomPrompt() {
    PlannerService service = serviceWithProducts(defaultProducts());
    PlanGenerationResponse res = service.generate(input("dnevni boravak s kaučem"));
    assertThat(res.completeKitchen()).isNull(); // NONE intent → no section, normal plan unaffected
}
```

- [ ] **Step 2: Run, verify fail.** `-Dtest=PlannerServiceTest` → the 4 new tests FAIL (method + wiring missing).

- [ ] **Step 3: Implement `buildCompleteKitchen`** on `PlannerService` (mirror `findSimilar`'s style — reuse `marketCatalog`, `selectedRetailers`, `scoreProduct`):

```java
private static final int MAX_KITCHEN_SETS = 6;

public CompleteKitchenDto buildCompleteKitchen(PlannerInputDto rawInput, KitchenIntentClassifier.KitchenBrief brief) {
    PlannerInputDto input = rawInput.normalized();
    List<String> allowed = selectedRetailers(input);
    List<Product> sets = marketCatalog(input).stream()
        .filter(p -> "kitchen-set".equalsIgnoreCase(p.getCategory()))
        .filter(ProductTaxonomy::canEnterPlanner)
        .filter(p -> allowed.contains(p.getRetailer()))
        .filter(p -> p.getPrice().doubleValue() <= input.budget())
        .sorted(Comparator.comparingDouble((Product p) -> scoreProduct(p, input, "value", Set.of(), 0)).reversed())
        .limit(MAX_KITCHEN_SETS)
        .toList();
    List<ProductDto> dtos = sets.stream().map(ProductDto::from).toList();
    return new CompleteKitchenDto(dtos, shapeKey(brief.shape()), brief.includeAppliances(), true);
}

private String shapeKey(KitchenIntentClassifier.KitchenShape shape) {
    return switch (shape) {
        case SINGLE_WALL -> "single-wall"; case L_SHAPED -> "l-shaped"; case U_SHAPED -> "u-shaped";
        case GALLEY -> "galley"; case ISLAND -> "island"; default -> "unknown";
    };
}
```

- [ ] **Step 4: Branch in the generate flow.** In `PlannerService.generate(...)` (and/or the shared `buildResponse(...)` it delegates to — read the current method first), classify the prompt and, when COMPLETE, attach the section:

```java
private final KitchenIntentClassifier kitchenClassifier = new KitchenIntentClassifier();
// ... inside generate/buildResponse, after the normal response is built:
KitchenIntentClassifier.KitchenBrief brief = kitchenClassifier.classify(input.prompt());
if (brief.intent() == KitchenIntentClassifier.KitchenIntent.COMPLETE) {
    response = response.withCompleteKitchen(buildCompleteKitchen(input, brief));
}
```
(`rawInput`/`input` naming: use the same normalized input the method already computed. If `generate` immediately delegates, add the branch where the final `PlanGenerationResponse` exists.)

- [ ] **Step 5: Run, verify pass.** `-Dtest=PlannerServiceTest` → all PASS (old + 4 new).

- [ ] **Step 6: Run the full backend suite** to catch regressions: backend test command with no `-Dtest`. Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit.**
```bash
git add backend/src/main/java/ai/budgetspace/planner/PlannerService.java backend/src/test/java/ai/budgetspace/planner/PlannerServiceTest.java
git commit -m "feat(kitchen): complete-kitchen selection + attach on COMPLETE intent"
```

---

## Task 5: Frontend — inline "Kompletna kuhinja" section

**Files:**
- Modify: `frontend/src/api/client.ts` (PlanGenerationResponse interface)
- Modify: `frontend/src/types/index.ts` (CompleteKitchen interface)
- Modify: `frontend/src/components/PlanResults.tsx` (render section)
- Modify: `frontend/src/i18n.ts` (keys)
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: `response.completeKitchen` from Task 3/4.
- Produces: the rendered section; `onProductClick`/analytics reused.

- [ ] **Step 1: Types.** In `frontend/src/types/index.ts`:
```ts
export interface CompleteKitchen {
  sets: Product[];
  shape: string;
  includeAppliances: boolean;
  showModularNote: boolean;
}
```
In `frontend/src/api/client.ts` add to `PlanGenerationResponse`: `completeKitchen?: CompleteKitchen | null;` (import the type).

- [ ] **Step 2: i18n keys** (hr + en) in `i18n.ts` — add:
```ts
'kitchen.completeTitle': { hr: 'Kompletna kuhinja', en: 'Complete kitchen' },
'kitchen.completeSubtitle': { hr: 'Gotovi modularni setovi unutar tvog budžeta', en: 'Ready modular sets within your budget' },
'kitchen.modularNote': { hr: 'Ovo su gotovi modularni setovi — ne konfigurirana kuhinja po mjeri. Za kuhinju po mjeri koristi IKEA-in planer kuhinje.', en: 'These are ready modular sets — not a configured made-to-measure kitchen. For a fitted kitchen, use IKEA’s kitchen planner.' },
'kitchen.plannerLink': { hr: 'Otvori IKEA planer kuhinje', en: 'Open IKEA kitchen planner' },
'kitchen.understood': { hr: 'Razumjeli smo: {shape}{appliances}', en: 'We understood: {shape}{appliances}' },
'kitchen.withAppliances': { hr: ' · s uređajima', en: ' · with appliances' },
'kitchen.emptySets': { hr: 'Nema kompletnog seta ispod {budget}. Probaj viši budžet ili pogledaj pojedinačne dijelove (uskoro).', en: 'No complete set under {budget}. Try a higher budget or individual parts (soon).' },
```
(Pass through the 12 message JSONs after — English fallback works meanwhile.)

- [ ] **Step 3: Render.** In `PlanResults.tsx`, add `completeKitchen?: CompleteKitchen` to `PlanResultsProps`, destructure it, and render a section ABOVE the normal plan block when present (reuse `productImage`, `productUrl`, `formatCurrency`, `ikeaMarketUrl`, `marketBadge`). Set cards reuse the product-card look. Show `kitchen.modularNote` + the IKEA-planner link always; show the empty message when `sets.length === 0`.

```tsx
{completeKitchen && (
  <section className="complete-kitchen" aria-label={t('kitchen.completeTitle')}>
    <div className="complete-kitchen-head">
      <strong>{t('kitchen.completeTitle')}</strong>
      <small>{t('kitchen.completeSubtitle')}</small>
    </div>
    <p className="kitchen-modular-note" role="note">{t('kitchen.modularNote')}</p>
    {completeKitchen.sets.length === 0 ? (
      <p className="complete-kitchen-empty">{t('kitchen.emptySets', { budget: formatCurrency(input.budget, input.market) })}</p>
    ) : (
      <div className="complete-kitchen-grid">
        {completeKitchen.sets.map((set) => {
          const openUrl = productUrl(set);
          return (
            <article className="kitchen-set-card" key={set.id}>
              <img src={productImage(set)} alt={usesFallbackImage(set) ? t('results.imageIllustrationAlt', { name: set.name }) : set.name}
                   loading="lazy" onError={(e) => handleProductImageError(e, set.category)} />
              <strong>{set.name}</strong>
              <div className="kitchen-set-meta"><span>{set.retailer}</span><strong>{formatCurrency(set.price, input.market)}</strong></div>
              {openUrl
                ? <a className="similar-open" href={openUrl} target="_blank" rel="noopener noreferrer" onClick={() => onProductClick(selectedPlan?.id ?? 'complete-kitchen', set)}>{t('similar.openProduct')} ↗</a>
                : <button type="button" className="similar-open" disabled>{t('results.productLinkUnavailable')}</button>}
            </article>
          );
        })}
      </div>
    )}
    <a className="kitchen-planner-link" href={ikeaMarketUrl(input.market)} target="_blank" rel="noopener noreferrer">{t('kitchen.plannerLink')} ↗</a>
  </section>
)}
```
(Place it at the top of the returned results, before the decision plan card. `selectedPlan` may not exist if plans is empty — guard the `onProductClick` planId as shown.)

- [ ] **Step 4: Pass the prop** from `Planner.tsx`: `completeKitchen={/* from the generate response state */}`. Add response state plumbing so the `completeKitchen` field from `generatePlan`/`generatePlanFast` reaches `PlanResults` (mirror how `secondHandSuggestions` flows).

- [ ] **Step 5: CSS** in `styles.css` — add `.complete-kitchen`, `.complete-kitchen-head`, `.kitchen-modular-note`, `.complete-kitchen-grid` (grid like `.similar-grid`), `.kitchen-set-card`, `.kitchen-planner-link` using theme tokens (mirror `.similar-*`).

- [ ] **Step 6: Build.** `cd frontend && npm run build`. Expected: tsc clean.

- [ ] **Step 7: Commit.**
```bash
git add frontend/src
git commit -m "feat(kitchen): inline complete-kitchen section in results"
```

---

## Task 6: Analytics

**Files:**
- Modify: `frontend/src/components/Planner.tsx` (+ pass a handler / fire on render)

**Interfaces:**
- Consumes: `trackEvent` (`frontend/src/utils/analytics.ts`), `trackProductClick`.

- [ ] **Step 1: Fire `kitchen_intent` + `complete_kitchen_view`** when a generate response carries `completeKitchen` (in the response-handling code in `Planner.tsx`, where `applyResponse` sets state):
```ts
if (response.completeKitchen) {
  trackEvent('kitchen_intent', { intent: 'complete', market: input.market ?? 'HR' });
  trackEvent('complete_kitchen_view', { set_count: response.completeKitchen.sets.length, budget: input.budget });
}
```
- [ ] **Step 2: `kitchen_set_click`** — extend the set-card `onProductClick` path (Task 5) to also fire `trackEvent('kitchen_set_click', { retailer: set.retailer, market: input.market ?? 'HR' })`. Simplest: add a `handleKitchenSetClick(set)` in `Planner.tsx` that calls `trackProductClick(planId, set, 'complete-kitchen')` + the two events, and pass it down (or reuse `handleSimilarProductOpen`-style centralization).
- [ ] **Step 3: Build** (`npm run build`) — tsc clean.
- [ ] **Step 4: Commit.**
```bash
git add frontend/src/components/Planner.tsx frontend/src/components/PlanResults.tsx
git commit -m "feat(kitchen): analytics for complete-kitchen view + set click"
```

---

## Task 7: Source verified modular kitchen sets (DE/AT/HR, IKEA)

**Files:**
- Create: `backend/src/main/resources/catalog/real-ikea-kitchen-sets-10-175.json`
- Modify: `backend/src/main/java/ai/budgetspace/product/RealCatalogSeeder.java` (register the JSON)

**Interfaces:**
- Consumes: the `kitchen-set` category (Task 1). Produces real `kitchen-set` rows in the seeded catalog.

**This is a research/verification task, not free code. Acceptance criteria are strict:**

- [ ] **Step 1: Read an existing catalog JSON** (e.g. `real-ikea-de-rooms.json`) to copy the exact row schema (id, name, retailer, category, price, market, roomTags, styleTags, productUrl, imageUrl, imageVerified, sourceReference, sourceType, dataQuality, lastCheckedAt, externalId…).

- [ ] **Step 2: Gather candidate IKEA modular kitchens** for DE, AT, HR — KNOXHULT (kitchen with doors/drawers), ENHET kitchens, and any complete-kitchen packages IKEA lists with a single price. For EACH candidate, fetch the real product page and record: exact name, current price in the market's currency, product URL, and `og:image` (only set `imageVerified: true` if the image was confirmed on the live page).

- [ ] **Step 3: Independently re-verify** every row (a second pass re-fetches each URL and confirms name + price match; default = reject on mismatch). Reject anything without a real URL + price. Target ≥ 3–6 verified sets per market (DE/AT/HR); fewer is fine if that's all that verifies — never pad.

- [ ] **Step 4: Write the JSON** with `category: "kitchen-set"`, `roomTags: "kitchen"`, correct `market`, `sourceReference` + `sourceType` set (so `isPlannerEligible` passes), `lastCheckedAt` = today. Register it in `RealCatalogSeeder`.

- [ ] **Step 5: Validate** — run the catalog integrity/runtime test that validates seeder JSON (find via `grep -rl "StoreLinkIntegrity\|RealCatalogSeeder" backend/src/test`). Expected: 0 import errors.

- [ ] **Step 6: Live-boot verify.** Recreate the backend container, then a `COMPLETE` prompt for DE/AT/HR returns the real sets:
```bash
docker restart budgetspace-backend   # wait for /actuator/health = UP
# POST /api/plans/generate-fast {prompt:"kompletna kuhinja do 3000 eura", budget:3000, roomType:"kitchen", market:"HR", ...}
# → response.completeKitchen.sets = the verified HR sets
```
Then browser-verify on :5173 (frontend-preview): a complete-kitchen prompt shows the section with real set cards + the modular note.

- [ ] **Step 7: Commit.**
```bash
git add backend/src/main/resources/catalog/real-ikea-kitchen-sets-10-175.json backend/src/main/java/ai/budgetspace/product/RealCatalogSeeder.java
git commit -m "feat(kitchen): verified IKEA modular kitchen sets (DE/AT/HR)"
```

---

## Task 8: Localize `kitchen.*` keys + TASKS.md

**Files:**
- Modify: `frontend/src/messages/*.json` (12 files)
- Modify: `TASKS.md`

- [ ] **Step 1: Translate** the new `kitchen.*` keys into the 12 languages (preserve `{shape}`, `{appliances}`, `{budget}` placeholders), validate all JSON parses, `npm run build`.
- [ ] **Step 2: TASKS.md** — mark the kitchen P0 progress: Increment 1 (complete-kitchen modular sets) done; note components/appliances/kitchenware increments remain. Add a Sprint 10.175 entry.
- [ ] **Step 3: Commit.**
```bash
git add frontend/src/messages TASKS.md
git commit -m "chore(kitchen): localize complete-kitchen copy + tasks log"
```

---

## Self-Review

**Spec coverage:** §4.1 classifier → Task 2. §4.2 complete-kitchen output → Tasks 3–4. §4.3 attributes (understanding) → Tasks 2 (parse) + 4/5 (surface). §4.4 taxonomy (`kitchen-set` only) → Task 1. §4.5 sourcing DE/AT/HR IKEA-only → Task 7. §4.6 UI inline section → Task 5. §8 analytics → Task 6. §6 honesty (modular note, no fabrication) → Tasks 5 (note) + 7 (acceptance criteria). Localization → Task 8. All covered.

**Placeholder scan:** No TBD/"handle edge cases"/"similar to Task N". Task 7 is intentionally a verification task with acceptance criteria (sourcing can't be pre-written as code), not a placeholder. Names to confirm-against-real-code are flagged explicitly (ProductTaxonomy API, PlanGenerationResponse copy-method style, categoryLabels shape).

**Type consistency:** `KitchenBrief(intent, shape, includeAppliances)` used identically in Tasks 2/4. `CompleteKitchenDto(sets, shape, includeAppliances, showModularNote)` consistent in Tasks 3/4/5. `kitchen-set` category string consistent across Tasks 1/4/7. Frontend `CompleteKitchen` interface matches the DTO fields.
