# Negation Handling and Graded Preferences — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every deterministic prompt parser negation-aware across all 15 markets, split "cheaper" from "cheapest" while keeping quality inside the price band, and turn "not too modern" into a real modern+classic blend.

**Architecture:** One new pure class (`NegationScope`) computes which character ranges of a normalized prompt sit inside a negated clause; the existing `applyX` methods consult it before accepting a match. Price intent gains a third level (`lower-price`) that is a gentler price pull, and both cheap paths gain a rating boost. Style gains an optional `secondaryStyles` list that the scorer rewards, produced by a "negation + degree" parser rule.

**Tech Stack:** Java 21 / Spring Boot (backend, JUnit 5 + AssertJ), TypeScript / React (frontend, vitest).

**Spec:** `docs/superpowers/specs/2026-07-20-negation-and-graded-preferences-design.md`

## Global Constraints

- Build backend with: `$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; & "C:\Users\bpusic\maven-tmp\apache-maven-3.9.9\bin\mvn.cmd" -s "C:\Users\bpusic\.m2\settings-central.xml" -o -f backend\pom.xml -Dtest=<Class> test`
- Frontend tests: `npm --prefix frontend test`. Frontend build: `npm --prefix frontend run build`. i18n gate: `node frontend/scripts/check-i18n.mjs`.
- Parser input is ALREADY normalized: lower-cased, NFD accent-stripped, and `æ→ae ø→o å→a ß→ss` folded. Write every pattern in plain ASCII.
- Copy rule (standing owner rule): no robotic/AI-sounding copy. Any user-facing string is hand-written, plain, and matches the surrounding editorial voice.
- Never blanket-update a failing assertion to make it green. If an existing assertion shifts, verify the new value is the intended behaviour and say so in the commit.
- Baseline before starting: 900 backend tests + 26 frontend tests green.

---

### Task 1: `NegationScope` core

**Files:**
- Create: `backend/src/main/java/ai/budgetspace/planner/NegationScope.java`
- Test: `backend/src/test/java/ai/budgetspace/planner/NegationScopeTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `static NegationScope of(String normalizedText)` and `boolean isNegated(int matchStart)`. Package-private class in `ai.budgetspace.planner`.

- [ ] **Step 1: Write the failing test**

```java
package ai.budgetspace.planner;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NegationScopeTest {

    private static int at(String text, String needle) {
        int i = text.indexOf(needle);
        if (i < 0) throw new IllegalArgumentException("needle not in text: " + needle);
        return i;
    }
    private static boolean negated(String text, String needle) {
        return NegationScope.of(text).isNegated(at(text, needle));
    }

    @Test
    void negatesWhatFollowsTheCue() {
        assertThat(negated("i do not want it cheap", "cheap")).isTrue();
        assertThat(negated("ne zelim tamno", "tamno")).isTrue();
        assertThat(negated("keine billigen moebel", "billigen")).isTrue();
    }

    @Test
    void plainAffirmativeTextIsNeverNegated() {
        assertThat(negated("dnevni boravak 1500, neka bude lijepo", "lijepo")).isFalse();
        assertThat(negated("just get me the cheapest sofa", "cheapest")).isFalse();
    }

    @Test
    void scopeStopsAtAClauseBreakAndAtAContrastWord() {
        // after the comma the sentence turns affirmative again
        assertThat(negated("ne zelim tamno, hocu svijetlo", "svijetlo")).isFalse();
        // "nego"/"but" end the negated part
        assertThat(negated("ne tamno nego svijetlo", "svijetlo")).isFalse();
        assertThat(negated("not dark but bright", "bright")).isFalse();
    }

    @Test
    void coordinatingAndKeepsTheNegationRunning() {
        assertThat(negated("ne zelim tamno i crno", "crno")).isTrue();
        assertThat(negated("i do not want dark and black", "black")).isTrue();
    }

    @Test
    void doubleNegationCancels() {
        assertThat(negated("nicht ohne ikea", "ikea")).isFalse();
        assertThat(negated("ne bez tepiha", "tepiha")).isFalse();
    }

    @Test
    void aTokenThatCONTAINSTheCueIsNotItselfNegated() {
        // these are our own positive signals that happen to be negative-shaped
        assertThat(negated("meubler pas cher", "pas cher")).isFalse();
        assertThat(negated("ne vise od dvije trgovine", "ne vise od dvije")).isFalse();
        assertThat(negated("bez puno obilazaka", "bez puno obilazaka")).isFalse();
    }

    @Test
    void shortCuesDoNotFireInsideLongerWords() {
        // HR "ne" must not match inside nema / neutralno / nekoliko
        assertThat(negated("nemam nista, neutralno i nekoliko polica", "polica")).isFalse();
        assertThat(negated("wohnzimmer, keine ahnung", "wohnzimmer")).isFalse(); // cue is AFTER the word
    }

    @Test
    void emptyAndNullAreSafe() {
        assertThat(NegationScope.of("").isNegated(0)).isFalse();
        assertThat(NegationScope.of(null).isNegated(0)).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `... -Dtest=NegationScopeTest test`
Expected: FAIL — `cannot find symbol: class NegationScope`.

- [ ] **Step 3: Write the implementation**

```java
package ai.budgetspace.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sprint 10.190: works out which character ranges of a prompt sit inside a NEGATED clause, so a parser never
 * applies a preference the user explicitly ruled out ("ne zelim tamno", "not cheap", "keine billigen Moebel").
 *
 * <p>Rules, in order:</p>
 * <ol>
 *   <li>The text is cut into clauses at {@code , ; . ! ?} or a CONTRAST conjunction ("ali/nego/but/aber/…").
 *       A coordinating "and" deliberately does NOT cut, so "ne zelim tamno i crno" negates both.</li>
 *   <li>Within a clause the negation cues are counted. An ODD count negates from the end of the FIRST cue to the
 *       end of the clause; an EVEN count cancels out, which is what makes "nicht ohne IKEA" a preference.</li>
 *   <li>Because the span starts at the END of the first cue, a token that CONTAINS the cue is never negated by
 *       it — "pas cher" (cheap), "ne vise od dvije" (store limit) and "bez obilazaka" (fewer stores) are our own
 *       positive signals and must survive.</li>
 * </ol>
 *
 * <p>Input MUST already be in the {@code PlannerIntentExtractor.normalize} form (lower-case, accent-stripped,
 * Nordic ligatures folded), so every pattern here is plain ASCII. Never throws.</p>
 */
final class NegationScope {

    // Negation cues across the 15 markets. Short/ambiguous ones are word-bounded, so the Croatian "ne" never
    // fires inside "nema"/"neutralno"/"nekoliko" and the English "no" never inside "north".
    private static final Pattern CUE = Pattern.compile(
            "\\bne\\b|\\bbez\\b|\\bnemoj\\w*|\\bnecu\\b|\\bnecemo\\b|\\bnikako\\b|\\bnije\\b|\\bnisam\\b"      // HR/BS/SR
            + "|\\bbrez\\b|\\bnocem\\b"                                                                        // SI
            + "|\\bnie\\b|\\bnechcem\\b"                                                                       // SK
            + "|\\bnot\\b|\\bno\\b|\\bdont\\b|\\bdoesnt\\b|\\bwithout\\b|\\bnothing\\b|\\bnever\\b"            // EN
            + "|\\bavoid\\b|\\bskip\\b"
            + "|\\bnicht\\b|\\bkein\\w*|\\bohne\\b"                                                            // DE
            + "|\\bnon\\b|\\bsenza\\b|\\bniente\\b"                                                            // IT
            + "|\\bsin\\b|\\bnada\\b|\\bnunca\\b"                                                              // ES
            + "|\\bnao\\b|\\bsem\\b"                                                                           // PT
            + "|\\bpas\\b|\\bsans\\b|\\baucun\\w*|\\bjamais\\b"                                                // FR
            + "|\\bniet\\b|\\bgeen\\b|\\bzonder\\b|\\bnooit\\b"                                                // NL
            + "|\\bei\\b|\\bala\\b|\\bilman\\b"                                                                // FI
            + "|\\binte\\b|\\bingen\\b|\\binga\\b|\\butan\\b|\\baldrig\\b"                                     // SV
            + "|\\bikke\\b|\\buten\\b|\\baldri\\b"                                                             // NO
            + "|\\buden\\b");                                                                                  // DK

    // A negation ends at a clause break or a CONTRAST conjunction. Note: the Portuguese "mas" (but) is
    // deliberately ABSENT — it collides with the far more common Spanish "mas" (more, as in "lo mas barato"),
    // and cutting there would end a Spanish negation early. Portuguese contrast still works via the comma.
    private static final Pattern BOUNDARY = Pattern.compile(
            "[,;.!?]|\\bali\\b|\\bnego\\b|\\bvec\\b|\\bbut\\b|\\baber\\b|\\bsondern\\b|\\bma\\b|\\bpero\\b"
            + "|\\bsino\\b|\\bmais\\b|\\bmaar\\b|\\bmutta\\b|\\bmen\\b|\\bale\\b|\\bvendar\\b");

    private static final NegationScope EMPTY = new NegationScope(List.of());

    private final List<int[]> negated; // {startInclusive, endExclusive}

    private NegationScope(List<int[]> negated) {
        this.negated = negated;
    }

    static NegationScope of(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) return EMPTY;
        List<int[]> spans = new ArrayList<>();
        for (int[] clause : clauses(normalizedText)) {
            String segment = normalizedText.substring(clause[0], clause[1]);
            Matcher cue = CUE.matcher(segment);
            int count = 0;
            int firstCueEnd = -1;
            while (cue.find()) {
                count++;
                if (firstCueEnd < 0) firstCueEnd = clause[0] + cue.end();
            }
            if (count % 2 == 1) spans.add(new int[]{firstCueEnd, clause[1]});
        }
        return spans.isEmpty() ? EMPTY : new NegationScope(spans);
    }

    /** True when a match STARTING at this offset falls inside a negated clause. */
    boolean isNegated(int matchStart) {
        for (int[] span : negated) {
            if (matchStart >= span[0] && matchStart < span[1]) return true;
        }
        return false;
    }

    // Substring (not Matcher.region) so \b behaves normally at the edges of every clause.
    private static List<int[]> clauses(String text) {
        List<int[]> out = new ArrayList<>();
        Matcher boundary = BOUNDARY.matcher(text);
        int start = 0;
        while (boundary.find()) {
            if (boundary.start() > start) out.add(new int[]{start, boundary.start()});
            start = boundary.end();
        }
        if (start < text.length()) out.add(new int[]{start, text.length()});
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `... -Dtest=NegationScopeTest test`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ai/budgetspace/planner/NegationScope.java backend/src/test/java/ai/budgetspace/planner/NegationScopeTest.java
git commit -m "parser: NegationScope — clause-scoped multilingual negation with double-negation parity (10.190)"
```

---

### Task 2: Wire negation into `PlannerIntentExtractor`

**Files:**
- Modify: `backend/src/main/java/ai/budgetspace/planner/PlannerIntentExtractor.java`
- Test: `backend/src/test/java/ai/budgetspace/planner/PlannerIntentExtractorTest.java`

**Interfaces:**
- Consumes: `NegationScope.of(...)`, `isNegated(int)` from Task 1.
- Produces: a private `boolean affirmative(String text, String regex, NegationScope scope)` used by the applyX methods; `enrich` unchanged externally.

- [ ] **Step 1: Write the failing test** (append to `PlannerIntentExtractorTest`)

```java
    @Test
    void negatedPreferencesAreNotApplied() {
        // style: the negated word must not set the style; the affirmative twin still must.
        assertThat(parse("Dnevni boravak, ne želim tamno.").style()).isNotEqualTo("industrial");
        assertThat(parse("Dnevni boravak, tamno i metalno.").style()).isEqualTo("industrial");
        // furnishing level
        assertThat(parse("Living room, not too basic please.").furnishingLevel()).isNotEqualTo("basic");
        assertThat(parse("Living room, just the basics.").furnishingLevel()).isEqualTo("basic");
        // colours: "bez crne boje" must not add black
        assertThat(parse("Dnevni boravak, bez crne boje.").colorPreferences()).doesNotContain("black");
        assertThat(parse("Dnevni boravak, crna boja.").colorPreferences()).contains("black");
        // room: a negated room word must not win
        assertThat(parse("Uredi mi dnevni boravak, ne spavaću sobu.").roomType()).isEqualTo("living-room");
    }

    @Test
    void negatedCheapMeansQualityNotLowestPrice() {
        // The one deliberate inversion: "I don't want cheap" is a quality signal.
        assertThat(parse("Dnevni boravak 2000 €, neću da bude jeftino.").optimizationGoal()).isEqualTo("style-match");
        assertThat(parse("Wohnzimmer 2000, keine billigen Möbel.").optimizationGoal()).isEqualTo("style-match");
        assertThat(parse("Living room 2000, nothing cheap.").optimizationGoal()).isEqualTo("style-match");
    }

    @Test
    void affirmativePathIsUnchangedByTheNegationScope() {
        // Regression guard: the positive signals from 10.189 must all still fire.
        assertThat(parse("So günstig wie möglich einrichten.").optimizationGoal()).isEqualTo("lowest-price");
        assertThat(parse("Le moins cher possible.").optimizationGoal()).isEqualTo("lowest-price");
        assertThat(parse("Make the bedroom cosy.").style()).isEqualTo("warm");
        assertThat(parse("Dnevni boravak, ne želim više od dvije trgovine.").maxStores()).isEqualTo(2);
        assertThat(parse("Dnevni boravak do 900 €, bez tepiha.").alreadyHaveCategories()).contains("rug");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `... -Dtest=PlannerIntentExtractorTest test`
Expected: FAIL on `negatedPreferencesAreNotApplied` (style is currently set to industrial by the negated "tamno").

- [ ] **Step 3: Implement**

In `enrich`, build the scope once and thread it through:

```java
        String text = normalize(input.prompt());
        if (text.isBlank()) {
            return input;
        }
        NegationScope scope = NegationScope.of(text);
```

Change the five affected calls to pass `scope`:
`applyRoom(text, input, scope)`, `applyStyle(text, input, scope)`, `applyFurnishingLevel(text, input, scope)`, `applyOptimizationGoal(text, input, scope)`, `applyColorAndMaterialPreferences(text, input, scope)`.
`applyRetailerIntent` and `applyCategories` keep their current signatures (Task 3 handles retailer parity; categories already model negation).

Add the helper next to `matches`:

```java
    // Sprint 10.190: like matches(), but a hit that sits inside a NEGATED clause does not count. Scans every
    // occurrence, so "ne zelim tamno, hocu tamni stol" still sees the affirmative second one.
    private boolean affirmative(String text, String regex, NegationScope scope) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        while (matcher.find()) {
            if (!scope.isNegated(matcher.start())) return true;
        }
        return false;
    }
```

Then inside those five methods replace every `matches(text, "…")` with `affirmative(text, "…", scope)`. `matches` itself stays — `applyRetailerIntent` still uses it.

For the one inversion, add at the END of `applyOptimizationGoal` (after the existing three rules, so it wins):

```java
        // Sprint 10.190: the single deliberate inversion. "neću jeftino" / "keine billigen Möbel" / "nothing
        // cheap" is a QUALITY signal, so it maps to the existing spend-up path rather than being merely ignored.
        // Detected as: a price-down token that IS negated (so it never fires on an affirmative "jeftino").
        String priceDown = "najjeftin|sto jeftin|low cost|jeftin|budget|gunstig|guenstig|billig|cheap|barat"
                + "|econom|pas cher|moins cher|bon marche";
        if (!affirmative(text, priceDown, scope) && matches(text, priceDown)) {
            input = input.withOptimizationGoal("style-match");
        }
        return input;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `... -Dtest=PlannerIntentExtractorTest test`
Expected: PASS, all methods.

- [ ] **Step 5: Run the neighbouring parser suites for regressions**

Run: `... "-Dtest=NegationScopeTest,PlannerIntentExtractorTest,AmountParserTest,PlannerRealUserPromptMatrixTest" test`
Expected: PASS. If a matrix case shifts, read that case and confirm the new value is correct before touching it.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/ai/budgetspace/planner/PlannerIntentExtractor.java backend/src/test/java/ai/budgetspace/planner/PlannerIntentExtractorTest.java
git commit -m "parser: room/style/level/goal/colour respect negation; 'not cheap' now means quality (10.190)"
```

---

### Task 3: Retailer double negation

**Files:**
- Modify: `backend/src/main/java/ai/budgetspace/planner/PlannerIntentExtractor.java` (`applyRetailerIntent`)
- Test: `backend/src/test/java/ai/budgetspace/planner/PlannerIntentExtractorTest.java`

**Interfaces:**
- Consumes: `NegationScope` (Task 1).
- Produces: no signature change; `applyRetailerIntentFromPrompt` behaviour only.

- [ ] **Step 1: Write the failing test**

```java
    @Test
    void doubleNegationTurnsAnExcludeIntoAPreference() {
        // "not without IKEA" means WITH IKEA — it must not exclude the store.
        PlannerInputDto de = retailerIntent("Wohnzimmer 2000, nicht ohne IKEA.");
        assertThat(de.excludedRetailers()).doesNotContain("IKEA");
        assertThat(de.preferredRetailers()).contains("IKEA");
        PlannerInputDto hr = retailerIntent("Dnevni boravak, ne bez IKEA.");
        assertThat(hr.excludedRetailers()).doesNotContain("IKEA");
        // Single negation still excludes (regression).
        assertThat(retailerIntent("Wohnzimmer 2000, ohne IKEA.").excludedRetailers()).contains("IKEA");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `... -Dtest=PlannerIntentExtractorTest#doubleNegationTurnsAnExcludeIntoAPreference test`
Expected: FAIL — IKEA is currently excluded.

- [ ] **Step 3: Implement**

In `applyRetailerIntent`, build a scope from the full text once, right after the IKEA-misspelling fold:

```java
        NegationScope retailerScope = NegationScope.of(text);
```

The exclude decision currently reads a `before` window. Compute whether that window's exclude cue is itself negated by checking the cue's absolute position. Replace the `excludeBefore` assignment's use site:

```java
            boolean excludeBefore = matches(before, EXCLUDE_CUES);
            // Sprint 10.190: "nicht ohne IKEA" / "ne bez IKEA" — the exclude cue itself sits in a negated clause,
            // so the two negations cancel and the store becomes a PREFERENCE instead of an exclusion.
            boolean excludeCancelled = excludeBefore && retailerScope.isNegated(idx - before.length());
            if (excludeCancelled) {
                preferred.add(retailer);
            } else if (excludeBefore || excludeAfter) {
                excluded.add(retailer);
            } else if (preferBefore || preferAfter) {
                preferred.add(retailer);
            }
```

Extract the existing exclude alternation into a `private static final String EXCLUDE_CUES = "…";` constant (same content as today, unchanged) so it is used by both `matches(before, …)` above and stays readable.

- [ ] **Step 4: Run test to verify it passes**

Run: `... -Dtest=PlannerIntentExtractorTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ai/budgetspace/planner/PlannerIntentExtractor.java backend/src/test/java/ai/budgetspace/planner/PlannerIntentExtractorTest.java
git commit -m "parser: 'nicht ohne IKEA' is a preference, not an exclusion (10.190)"
```

---

### Task 4: Kitchen intent respects negation

**Files:**
- Modify: `backend/src/main/java/ai/budgetspace/planner/KitchenIntentClassifier.java`
- Test: `backend/src/test/java/ai/budgetspace/planner/KitchenIntentClassifierTest.java`

**Interfaces:**
- Consumes: `NegationScope` (Task 1). Note `KitchenIntentClassifier.normalize` already folds the Nordic ligatures, so its output satisfies `NegationScope`'s input contract.
- Produces: `classify(String)` unchanged externally.

- [ ] **Step 1: Write the failing test**

```java
    @Test
    void aNegatedCompleteKitchenIsNotACompleteKitchen() {
        // "I don't want a whole kitchen, just an oven" → the oven is a COMPONENT ask.
        assertThat(c.classify("ne želim kompletnu kuhinju, samo pećnicu").intent()).isEqualTo(KitchenIntent.COMPONENT);
        assertThat(c.classify("not a whole kitchen, just a dishwasher").intent()).isEqualTo(KitchenIntent.COMPONENT);
        // Double negation still routes to COMPLETE.
        assertThat(c.classify("nicht ohne eine komplette Küche").intent()).isEqualTo(KitchenIntent.COMPLETE);
        // Affirmative regression.
        assertThat(c.classify("trebam kompletnu kuhinju do 3000 eura").intent()).isEqualTo(KitchenIntent.COMPLETE);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `... -Dtest=KitchenIntentClassifierTest test`
Expected: FAIL — the negated prompt currently classifies COMPLETE.

- [ ] **Step 3: Implement**

In `classify`, gate the COMPLETE branch on an affirmative qualifier:

```java
    public KitchenBrief classify(String prompt) {
        String p = normalize(prompt);
        NegationScope scope = NegationScope.of(p);
        KitchenIntent intent;
        // Sprint 10.190: the qualifier must not sit in a negated clause — "ne želim kompletnu kuhinju, samo
        // pećnicu" is a COMPONENT ask. KNOXHULT is a product line, so it is taken at face value.
        boolean completeAsk = KNOXHULT.matcher(p).find()
                || (KITCHEN_WORD.matcher(p).find() && affirmative(p, COMPLETE_QUALIFIER, scope));
        if (completeAsk) {
            intent = KitchenIntent.COMPLETE;
        } else if (COMPONENT.matcher(p).find()) {
            intent = KitchenIntent.COMPONENT;
        } else if (KITCHENWARE.matcher(p).find()) {
            intent = KitchenIntent.KITCHENWARE;
        } else {
            intent = KitchenIntent.NONE;
        }
        return new KitchenBrief(intent, parseShape(p), parseAppliances(p));
    }

    private boolean affirmative(String text, Pattern pattern, NegationScope scope) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (!scope.isNegated(matcher.start())) return true;
        }
        return false;
    }
```

Add `import java.util.regex.Matcher;` if absent.

- [ ] **Step 4: Run test to verify it passes**

Run: `... -Dtest=KitchenIntentClassifierTest test`
Expected: PASS, all methods.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ai/budgetspace/planner/KitchenIntentClassifier.java backend/src/test/java/ai/budgetspace/planner/KitchenIntentClassifierTest.java
git commit -m "parser: a negated complete-kitchen ask routes to COMPONENT (10.190)"
```

---

### Task 5: Price intent — split comparative from superlative

**Files:**
- Modify: `backend/src/main/java/ai/budgetspace/planner/PlannerIntentExtractor.java` (`applyOptimizationGoal`)
- Test: `backend/src/test/java/ai/budgetspace/planner/PlannerIntentExtractorTest.java`

**Interfaces:**
- Produces: `optimizationGoal` may now be the new value `"lower-price"`. Task 6 (scoring) and Task 11 (frontend) consume it.

- [ ] **Step 1: Write the failing test**

```java
    @Test
    void cheaperIsNotTheSameAsCheapest() {
        // comparative / plain → a gentler downshift
        assertThat(parse("Dnevni boravak, može malo jeftinije.").optimizationGoal()).isEqualTo("lower-price");
        assertThat(parse("Living room, something cheaper.").optimizationGoal()).isEqualTo("lower-price");
        assertThat(parse("Wohnzimmer, günstiger bitte.").optimizationGoal()).isEqualTo("lower-price");
        assertThat(parse("Salón, algo barato.").optimizationGoal()).isEqualTo("lower-price");
        // superlative → the floor
        assertThat(parse("Dnevni boravak, što jeftinije.").optimizationGoal()).isEqualTo("lowest-price");
        assertThat(parse("Just get me the cheapest sofa.").optimizationGoal()).isEqualTo("lowest-price");
        assertThat(parse("So günstig wie möglich einrichten.").optimizationGoal()).isEqualTo("lowest-price");
        assertThat(parse("Lo más barato posible.").optimizationGoal()).isEqualTo("lowest-price");
        assertThat(parse("Le moins cher possible.").optimizationGoal()).isEqualTo("lowest-price");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — every case currently returns `lowest-price`.

- [ ] **Step 3: Implement**

Replace the single lowest-price rule in `applyOptimizationGoal` with two, superlative checked LAST so it wins:

```java
        // Sprint 10.190: "jeftinije/cheaper" asks for a lower price BAND, not the floor — the owner's point that
        // a cheaper plan should still look for the best piece it can afford. Only the superlative goes to the floor.
        if (affirmative(text, "jeftin|cheap|gunstig|guenstig|billig|barat|econom|pas cher|moins cher|bon marche"
                + "|low cost|budget", scope)) {
            input = input.withOptimizationGoal("lower-price");
        }
        if (affirmative(text, "najjeftin|sto jeftin|cheapest|am gunstigsten|so gunstig wie moglich"
                + "|lo mas barato|il piu economico|le moins cher|mais barato possivel|sto povoljnije", scope)) {
            input = input.withOptimizationGoal("lowest-price");
        }
```

Keep `best-value` and `style-match` rules as they are, and keep the Task 2 inversion block last.

Also update `PlannerService.prefersFewStores`-adjacent logic is NOT affected; no change needed there.

- [ ] **Step 4: Run test to verify it passes**

Run: `... "-Dtest=PlannerIntentExtractorTest,PlannerRealUserPromptMatrixTest" test`
Expected: PASS. The 10.189 test `recognisesLevelGoalAndStyleAcrossLanguages` asserts `lowest-price` for "So günstig wie möglich", "Just get me the cheapest", "Lo más barato posible", "Le moins cher possible" — all four are superlatives and must still pass. Its "barato"-only case must now be updated to `lower-price`; verify each individually.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ai/budgetspace/planner/PlannerIntentExtractor.java backend/src/test/java/ai/budgetspace/planner/PlannerIntentExtractorTest.java
git commit -m "parser: split 'cheaper' (lower-price) from 'cheapest' (lowest-price) (10.190)"
```

---

### Task 6: Price intent — scoring

**Files:**
- Modify: `backend/src/main/java/ai/budgetspace/planner/PlannerService.java` (`score`, ~line 1366)
- Test: `backend/src/test/java/ai/budgetspace/planner/PlannerServiceTest.java`

**Interfaces:**
- Consumes: `optimizationGoal == "lower-price"` from Task 5.

- [ ] **Step 1: Write the failing test**

```java
    @Test
    void lowerPriceIsGentlerThanLowestPriceAndBothKeepQuality() {
        // Same catalog + budget: "lower-price" should land ABOVE the floor that "lowest-price" produces.
        BigDecimal lower = service.generate(input("dnevni boravak, malo jeftinije").withBudget(1500))
                .plans().get(0).total();
        BigDecimal lowest = service.generate(input("dnevni boravak, sto jeftinije").withBudget(1500))
                .plans().get(0).total();
        assertThat(lower).as("a cheaper plan is not the cheapest plan").isGreaterThan(lowest);
    }
```

Add a second test using the existing test-catalog helpers: two candidates in the same category, near-identical price, clearly different rating; assert the better-rated one is chosen under `lowest-price`. Follow the existing `PlannerServiceTest` fixture style (see the `budgetPick`/`bestValue` tests around line 229 for how products are built).

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — `lower-price` is not handled in `score`, so it falls to the `value` branch and the totals will not differ as asserted (or the rating case picks the cheaper item).

- [ ] **Step 3: Implement**

In `score`, extend the price-bias chain:

```java
        } else if (input.optimizationGoal().equals("lowest-price") || mode.equals("budget")) {
            priceBias = Math.max(0, 34 - price / 18);
        } else if (input.optimizationGoal().equals("lower-price")) {
            // Sprint 10.190: a gentle downshift, not a floor — "jeftinije" means a lower price band while the
            // rating term below still decides between comparable pieces.
            priceBias = Math.max(0, 18 - price / 40);
        } else if (mode.equals("value")) {
```

And add the quality-in-band term next to `ratingScore`:

```java
        double ratingScore = product.getRating() * 5;
        // Sprint 10.190: inside a cheap plan, a clearly better-rated piece should beat one that is a few euro
        // cheaper — the owner's "even when they ask for cheap, still try to find something good".
        boolean cheapPath = input.optimizationGoal().equals("lower-price")
                || input.optimizationGoal().equals("lowest-price");
        double qualityInBand = cheapPath ? product.getRating() * 6 : 0;
```

Include `qualityInBand` in the returned sum.

- [ ] **Step 4: Run test to verify it passes**

Run: `... -Dtest=PlannerServiceTest test`
Expected: PASS. Constants (18 / 40 / 6) may need one round of tuning — adjust and re-run until both assertions hold, then leave a comment stating the observed effect.

- [ ] **Step 5: Run the full backend suite**

Run: `... test`
Expected: 900+ pass. Scoring changes can move catalog-runtime plan totals; inspect any shift individually and confirm it is the intended direction before accepting.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/ai/budgetspace/planner/PlannerService.java backend/src/test/java/ai/budgetspace/planner/PlannerServiceTest.java
git commit -m "planner: gentler price pull for lower-price + rating weight inside cheap plans (10.190)"
```

---

### Task 7: `secondaryStyles` on the DTO

**Files:**
- Modify: `backend/src/main/java/ai/budgetspace/dto/PlannerInputDto.java`
- Test: `backend/src/test/java/ai/budgetspace/dto/PlannerInputDtoBackCompatTest.java` (create)

**Interfaces:**
- Produces: record component `List<String> secondaryStyles()`, builder setter `secondaryStyles(...)`, and `PlannerInputDto withSecondaryStyles(List<String>)`. Tasks 8 and 9 consume these.

- [ ] **Step 1: Write the failing test**

```java
package ai.budgetspace.dto;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PlannerInputDtoBackCompatTest {

    @Test
    void legacyConstructorsStillCompileAndDefaultSecondaryStyles() {
        // 16-arg (pre-10.7) signature
        PlannerInputDto legacy16 = new PlannerInputDto("", 1500, "living-room", "bright", "Zagreb", 20, "multi",
                List.of("IKEA"), "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0);
        assertThat(legacy16.secondaryStyles()).isEmpty();

        // 19-arg (pre-10.120) signature
        PlannerInputDto legacy19 = new PlannerInputDto("", 1500, "living-room", "bright", "Zagreb", 20, "multi",
                List.of("IKEA"), "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0,
                List.of(), List.of(), "HR");
        assertThat(legacy19.secondaryStyles()).isEmpty();
    }

    @Test
    void withSecondaryStylesPreservesEverythingElse() {
        PlannerInputDto base = new PlannerInputDto("kuhinja", 2000, "kitchen", "modern", "Zagreb", 20, "multi",
                List.of("IKEA"), "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0);
        PlannerInputDto blended = base.withSecondaryStyles(List.of("classic"));
        assertThat(blended.secondaryStyles()).containsExactly("classic");
        assertThat(blended.style()).isEqualTo("modern");
        assertThat(blended.budget()).isEqualTo(2000);
        assertThat(blended.roomType()).isEqualTo("kitchen");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `... -Dtest=PlannerInputDtoBackCompatTest test`
Expected: FAIL — `secondaryStyles()` does not exist.

- [ ] **Step 3: Implement**

Add the component to the canonical record, immediately after `materialPreferences`:

```java
        // Sprint 10.190: a SOFTENED style ("ne previše moderno") keeps `style` as the primary and lists the
        // complementary style here, so the scorer can reward a piece that reads as both. Empty for every
        // ordinary request, so existing callers and previously saved plans are unaffected.
        List<String> secondaryStyles,
```

In the compact constructor, default it: `secondaryStyles = secondaryStyles == null ? List.of() : secondaryStyles;` (match the existing null-guard style used for the other lists).

In BOTH back-compat constructors, pass `List.of()` in the new position. In `Builder`, add the field, the setter, copy it in `Builder(PlannerInputDto source)` and in `build()`. Add:

```java
    public PlannerInputDto withSecondaryStyles(List<String> nextSecondaryStyles) {
        return copy().secondaryStyles(nextSecondaryStyles).build();
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `... "-Dtest=PlannerInputDtoBackCompatTest,MoveInDtoBackCompatTest" test`
Expected: PASS.

- [ ] **Step 5: Compile the whole backend to prove no call site broke**

Run: `... test`
Expected: 900+ pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/ai/budgetspace/dto/PlannerInputDto.java backend/src/test/java/ai/budgetspace/dto/PlannerInputDtoBackCompatTest.java
git commit -m "dto: optional secondaryStyles with back-compatible constructors (10.190)"
```

---

### Task 8: Parse a softened style into a blend

**Files:**
- Modify: `backend/src/main/java/ai/budgetspace/planner/PlannerIntentExtractor.java` (`applyStyle`)
- Test: `backend/src/test/java/ai/budgetspace/planner/PlannerIntentExtractorTest.java`

**Interfaces:**
- Consumes: `withSecondaryStyles` (Task 7), `NegationScope` (Task 1).
- Produces: `style` = the softened style, `secondaryStyles` = `[complement]`. Task 9 scores it.

- [ ] **Step 1: Write the failing test**

```java
    @Test
    void notTooModernBecomesAModernClassicBlend() {
        PlannerInputDto soft = parse("Dnevni boravak 2000 €, ne previše moderno.");
        assertThat(soft.style()).isEqualTo("modern");
        assertThat(soft.secondaryStyles()).containsExactly("classic");

        assertThat(parse("Living room, not too modern.").secondaryStyles()).containsExactly("classic");
        assertThat(parse("Wohnzimmer, nicht zu modern.").secondaryStyles()).containsExactly("classic");
        assertThat(parse("Soggiorno, non troppo moderno.").secondaryStyles()).containsExactly("classic");
        // minimal softens toward warm
        assertThat(parse("Spavaća soba, ne previše minimalistički.").secondaryStyles()).containsExactly("warm");
        // a plain style request stays single
        assertThat(parse("Dnevni boravak, moderno.").secondaryStyles()).isEmpty();
        assertThat(parse("Dnevni boravak, moderno.").style()).isEqualTo("modern");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — `secondaryStyles` is always empty and the negated "moderno" is currently suppressed by Task 2.

- [ ] **Step 3: Implement**

Add to `PlannerIntentExtractor`:

```java
    // Sprint 10.190: "ne previše X" is not a plain negation — the user wants a SOFTER X, not no X. Detected as a
    // degree phrase immediately followed (within a short window) by a style word; the result keeps X as the
    // primary style and adds its complement, so the scorer can favour a piece that reads as both.
    private static final Pattern SOFTENER = Pattern.compile(
            "\\b(?:ne previse|nije pretjerano|ne bas|not too|nothing too|not overly|nicht zu|non troppo"
            + "|no demasiado|nao muito|pas trop|niet te|ei liian|inte for|ikke for)\\b");

    private static final Map<String, String> STYLE_COMPLEMENT = Map.of(
            "modern", "classic",
            "classic", "modern",
            "minimal", "warm",
            "warm", "bright",
            "bright", "warm",
            "industrial", "warm",
            "boho", "minimal");
```

At the END of `applyStyle`, after the existing rules:

```java
        Matcher softener = SOFTENER.matcher(text);
        while (softener.find()) {
            // look only at the short window right after the degree phrase, so "ne previše moderno, i topli tepih"
            // softens the style without swallowing the rest of the sentence
            String window = text.substring(softener.end(), Math.min(text.length(), softener.end() + 24));
            String softened = styleIn(window);
            if (softened != null) {
                return input.withStyle(softened).withSecondaryStyles(
                        List.of(STYLE_COMPLEMENT.getOrDefault(softened, "warm")));
            }
        }
        return input;
```

And the small lookup it needs:

```java
    private String styleIn(String window) {
        if (matches(window, "moder|uredno")) return "modern";
        if (matches(window, "minimal|jednostavn|cisto")) return "minimal";
        if (matches(window, "classic|klasic|klasc|klassi")) return "classic";
        if (matches(window, "industrial|industrij|tamno|crno|metal")) return "industrial";
        if (matches(window, "boho|prirodn|biljk|ratan|natural")) return "boho";
        if (matches(window, "svijetl|prozrac|skandi|scandi|nordic|skandinav")) return "bright";
        if (matches(window, "toplo|ugodno|mekano|domac|cozy|cosy|\\bwarm\\b|\\bgemue?tlich")) return "warm";
        return null;
    }
```

Because this runs last and returns directly, it wins over the earlier suppression from Task 2.

- [ ] **Step 4: Run test to verify it passes**

Run: `... -Dtest=PlannerIntentExtractorTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ai/budgetspace/planner/PlannerIntentExtractor.java backend/src/test/java/ai/budgetspace/planner/PlannerIntentExtractorTest.java
git commit -m "parser: 'ne previše moderno' yields a modern+classic blend, not a dropped style (10.190)"
```

---

### Task 9: Score the style blend

**Files:**
- Modify: `backend/src/main/java/ai/budgetspace/planner/PlannerService.java` (`score`, ~line 1341)
- Test: `backend/src/test/java/ai/budgetspace/planner/PlannerServiceTest.java`

**Interfaces:**
- Consumes: `input.secondaryStyles()` (Task 7), populated by Task 8.

- [ ] **Step 1: Write the failing test**

Build three products in the same category and price: one tagged only `modern`, one only `classic`, one tagged BOTH. Generate with `style=modern`, `secondaryStyles=[classic]`. Assert the BOTH-tagged product is the pick.

```java
    @Test
    void aSoftenedStylePrefersAPieceThatReadsAsBoth() {
        PlannerInputDto blended = input("dnevni boravak")
                .withStyle("modern")
                .withSecondaryStyles(List.of("classic"));
        FurnishingPlanDto plan = service.generate(blended).plans().get(0);
        assertThat(plan.items()).extracting(item -> item.product().id()).contains("sofa-modern-classic");
    }
```

(Register `sofa-modern-classic`, `sofa-modern-only`, `sofa-classic-only` in the test catalog at identical prices, following the existing fixture helper in this file.)

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — style scoring is binary, so the blend has no advantage and the tie breaks arbitrarily.

- [ ] **Step 3: Implement**

Replace the single `styleScore` line:

```java
        // Sprint 10.190: a softened style ("ne previše moderno") carries a complementary secondary style. A piece
        // that reads as BOTH is the ideal answer, a secondary-only piece is still a good fit, and the old binary
        // 38/12 is preserved for the ordinary single-style request (secondaryStyles is then empty).
        boolean primary = styleMatches(product, input.style());
        boolean secondary = input.secondaryStyles().stream().anyMatch(s -> styleMatches(product, s));
        double styleScore = primary && secondary ? 44 : primary ? 38 : secondary ? 30 : 12;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `... -Dtest=PlannerServiceTest test`
Expected: PASS.

- [ ] **Step 5: Run the full backend suite**

Run: `... test`
Expected: 900+ pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/ai/budgetspace/planner/PlannerService.java backend/src/test/java/ai/budgetspace/planner/PlannerServiceTest.java
git commit -m "planner: reward pieces matching both the primary and the complementary style (10.190)"
```

---

### Task 10: Frontend negation scope

**Files:**
- Create: `frontend/src/utils/negationScope.ts`
- Create: `frontend/src/utils/negationScope.test.ts`
- Modify: `frontend/src/utils/outOfScope.ts`, `frontend/src/utils/multiRoom.ts`
- Modify: `frontend/src/utils/outOfScope.test.ts`, `frontend/src/utils/multiRoom.test.ts`

**Interfaces:**
- Produces: `export function negatedRanges(text: string): Array<[number, number]>` and `export function isNegated(ranges, index): boolean`, plus `export function normalizeForMatch(prompt: string): string` (the shared lower-case + NFD + ø/æ fold used by both detectors today).

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it } from 'vitest';
import { normalizeForMatch, negatedRanges, isNegated } from './negationScope';

const neg = (text: string, needle: string) => {
  const t = normalizeForMatch(text);
  return isNegated(negatedRanges(t), t.indexOf(normalizeForMatch(needle)));
};

describe('negationScope', () => {
  it('negates what follows a cue, in every market language', () => {
    expect(neg('ne trebam perilicu', 'perilicu')).toBe(true);
    expect(neg('i do not want a washing machine', 'washing machine')).toBe(true);
    expect(neg('keine waschmaschine', 'waschmaschine')).toBe(true);
  });

  it('stops at a clause break or contrast word', () => {
    expect(neg('ne trebam perilicu, trebam kauc', 'kauc')).toBe(false);
    expect(neg('not a tv but a tv stalak', 'tv stalak')).toBe(false);
  });

  it('cancels on double negation', () => {
    expect(neg('nicht ohne balkon', 'balkon')).toBe(false);
  });

  it('leaves plain affirmative text alone', () => {
    expect(neg('trebam perilicu posuda', 'perilicu')).toBe(false);
  });
});
```

Also extend `outOfScope.test.ts`:

```ts
  it('does not flag a negated out-of-scope mention', () => {
    expect(detectOutOfScope('ne trebam perilicu, samo namjestaj')).toBeNull();
    expect(detectOutOfScope('no tv needed, just a tv stalak')).toBeNull();
  });
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix frontend test`
Expected: FAIL — module `./negationScope` not found.

- [ ] **Step 3: Implement**

Create `negationScope.ts` as a direct port of `NegationScope.java` — same cue list, same boundary list (including the deliberate omission of PT "mas"), same odd-parity rule, same "span starts at the end of the first cue" rule. Export `normalizeForMatch` containing exactly the normalization the two detectors use today (`toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu,'').replace(/ø/g,'o').replace(/æ/g,'ae')`).

Then in `outOfScope.ts` and `multiRoom.ts`, replace the inline normalization with `normalizeForMatch(prompt)`, compute `const ranges = negatedRanges(text)` once, and accept a pattern hit only when `!isNegated(ranges, matchIndex)`. Use `RegExp.exec` (with the `g` flag on a local copy) rather than `.test`, so the match index is available.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix frontend test`
Expected: PASS, all files.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/utils/negationScope.ts frontend/src/utils/negationScope.test.ts frontend/src/utils/outOfScope.ts frontend/src/utils/multiRoom.ts frontend/src/utils/outOfScope.test.ts frontend/src/utils/multiRoom.test.ts
git commit -m "frontend: shared negation scope so a negated mention raises no banner or nudge (10.190)"
```

---

### Task 11: Frontend types, goal option and copy

**Files:**
- Modify: `frontend/src/types/index.ts:72` (`OptimizationGoal`) and the `PlannerInput` interface (~line 208)
- Modify: `frontend/src/components/PlannerForm.tsx:72-77` (`optimizationGoals`)
- Modify: `frontend/src/messages/*.json` (14 locales) and the Croatian defaults in `frontend/src/i18n.ts`

**Interfaces:**
- Consumes: the `lower-price` value produced by Task 5.

- [ ] **Step 1: Extend the type and the interface**

```ts
export type OptimizationGoal = 'lowest-price' | 'lower-price' | 'best-value' | 'least-stores' | 'style-match';
```

Add to `PlannerInput`: `secondaryStyles?: string[];`

- [ ] **Step 2: Add the form option**

Insert between `best-value` and `lowest-price` in `optimizationGoals`:

```ts
  { value: 'lower-price', label: 'form.goalLowerPriceLabel', description: 'form.goalLowerPriceDescription' },
```

- [ ] **Step 3: Write the copy**

Hand-written, plain, matching the existing goal copy voice — no marketing tone. Croatian and English first:

- `form.goalLowerPriceLabel` — HR: `Niža cijena` · EN: `Lower price`
- `form.goalLowerPriceDescription` — HR: `Spušta cijenu, ali i dalje bira najbolje što stane.` · EN: `Brings the price down while still picking the best that fits.`

Then the remaining 12 locales (de, it, fi, fr, nl, sk, es, pt, no, sv, da, sl), keeping each description to one short sentence in that language's own voice.

- [ ] **Step 4: Verify key parity and build**

Run: `node frontend/scripts/check-i18n.mjs`
Expected: PASS (no missing keys in any locale).
Run: `npm --prefix frontend run build`
Expected: exit 0.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/types/index.ts frontend/src/components/PlannerForm.tsx frontend/src/i18n.ts frontend/src/messages
git commit -m "frontend: 'Lower price' goal option + secondaryStyles on the input type (10.190)"
```

---

### Task 12: Negation register in the prompt matrix

**Files:**
- Modify: `backend/src/test/resources/prompts/prompt-matrix.json`
- Modify: `backend/src/test/java/ai/budgetspace/planner/PlannerRealUserPromptMatrixTest.java`

- [ ] **Step 1: Extend `PromptCase` with the two fields the matrix cannot currently assert**

```java
            List<String> preferredRetailers, Integer maxStores, String style,
            String furnishingLevel, String optimizationGoal) {
```

And the assertions:

```java
        if (c.furnishingLevel() != null) {
            assertThat(enriched.furnishingLevel()).as("%s furnishingLevel", c.id()).isEqualTo(c.furnishingLevel());
        }
        if (c.optimizationGoal() != null) {
            assertThat(enriched.optimizationGoal()).as("%s optimizationGoal", c.id()).isEqualTo(c.optimizationGoal());
        }
```

- [ ] **Step 2: Add at least 20 cases with `"register": "negation"`**

Spread across markets, each a realistic negated prompt with its expected outcome, e.g.:

```json
  {
    "id": "hr-neg-01",
    "lang": "hr",
    "market": "HR",
    "register": "negation",
    "prompt": "Dnevni boravak 2000 €, neću da bude jeftino.",
    "room": "living-room",
    "budget": 2000,
    "optimizationGoal": "style-match"
  },
```

- [ ] **Step 3: Raise the coverage floor**

In `matrixHasStrongCoverage`, add:

```java
        long negation = cases.stream().filter(c -> "negation".equals(c.register())).count();
        assertThat(negation).as("negation cases").isGreaterThanOrEqualTo(20L);
```

- [ ] **Step 4: Run**

Run: `... -Dtest=PlannerRealUserPromptMatrixTest test`
Expected: PASS with the case count above 360.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/resources/prompts/prompt-matrix.json backend/src/test/java/ai/budgetspace/planner/PlannerRealUserPromptMatrixTest.java
git commit -m "test: negation register in the prompt matrix + level/goal assertions (10.190)"
```

---

### Task 13: Full verification and housekeeping

**Files:**
- Modify: the three 10.189 comments dated `2026-07-18` in `KitchenIntentClassifier.java`, `PlannerIntentExtractor.java`, `AmountParser.java` → `2026-07-20`
- Modify: `C:\Users\bpusic\.claude\projects\D--bpusic-...\memory\sprint-10-189-prompt-synonym-audit.md` (the line calling negation an accepted limitation is now stale)

- [ ] **Step 1: Fix the three dated comments**

- [ ] **Step 2: Full backend suite**

Run: `... test`
Expected: BUILD SUCCESS, 900+ tests, 0 failures.

- [ ] **Step 3: Full frontend suite and build**

Run: `npm --prefix frontend test` then `npm --prefix frontend run build`
Expected: all vitest files pass; build exit 0.

- [ ] **Step 4: Update the 10.189 memory note and write the 10.190 memory**

Replace the "negation FPs … not worth a negation parser pre-traffic" line with a pointer to this sprint, and add a `sprint-10-190-negation-graded-preferences` memory plus its `MEMORY.md` index line.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: correct 10.189 comment dates and record the 10.190 outcome"
```

---

## Self-review

**Spec coverage:** Part A → Tasks 1–4 and 10. Part B → Tasks 5, 6, 11. Part C → Tasks 7, 8, 9, 11. Part D → the test step inside every task plus Tasks 12 and 13. The spec's "AmountParser deliberately not scoped" note is honoured — no task touches it. The spec's follow-up note about the stale 10.189 memory line and the three comment dates is Task 13.

**Placeholder scan:** No TBD/TODO. Two steps describe fixtures rather than quoting them in full (Task 6 Step 1's rating fixture, Task 9 Step 1's three products) because they must follow the existing `PlannerServiceTest` catalog helper, whose shape is local to that file; both name the exact product ids and the exact assertion. Task 11 Step 3 names the exact keys and gives the HR/EN strings verbatim.

**Type consistency:** `NegationScope.of` / `isNegated(int)` are used with those exact names in Tasks 2, 3, 4. `withSecondaryStyles(List<String>)` and `secondaryStyles()` from Task 7 are used verbatim in Tasks 8 and 9. The frontend trio `normalizeForMatch` / `negatedRanges` / `isNegated` from Task 10 is used with those names in its own tests. The goal value string `lower-price` is identical in Tasks 5, 6 and 11.
