package ai.budgetspace.planner;

import org.junit.jupiter.api.Test;

import ai.budgetspace.planner.KitchenIntentClassifier.KitchenIntent;
import ai.budgetspace.planner.KitchenIntentClassifier.KitchenShape;

import static org.assertj.core.api.Assertions.assertThat;

class KitchenIntentClassifierTest {
    private final KitchenIntentClassifier c = new KitchenIntentClassifier();

    @Test
    void detectsCompleteKitchenAcrossLanguages() {
        assertThat(c.classify("trebam kompletnu kuhinju do 3000 eura").intent()).isEqualTo(KitchenIntent.COMPLETE);
        assertThat(c.classify("ich brauche eine komplette Einbauküche").intent()).isEqualTo(KitchenIntent.COMPLETE);
        assertThat(c.classify("i want a complete modular kitchen").intent()).isEqualTo(KitchenIntent.COMPLETE);
    }

    @Test
    void detectsComponentIntent() {
        assertThat(c.classify("treba mi perilica posuđa").intent()).isEqualTo(KitchenIntent.COMPONENT);
        assertThat(c.classify("suche einen Backofen").intent()).isEqualTo(KitchenIntent.COMPONENT);
    }

    @Test
    void detectsKitchenwareIntent() {
        assertThat(c.classify("trebam tanjure i čaše").intent()).isEqualTo(KitchenIntent.KITCHENWARE);
    }

    @Test
    void nonKitchenPromptIsNone() {
        assertThat(c.classify("dnevni boravak s kaučem i tepihom").intent()).isEqualTo(KitchenIntent.NONE);
        assertThat(c.classify("asdfghjkl").intent()).isEqualTo(KitchenIntent.NONE);
    }

    @Test
    void parsesShapeAndAppliances() {
        var b = c.classify("kompletna L kuhinja s uređajima");
        assertThat(b.intent()).isEqualTo(KitchenIntent.COMPLETE);
        assertThat(b.shape()).isEqualTo(KitchenShape.L_SHAPED);
        assertThat(b.includeAppliances()).isTrue();
        assertThat(c.classify("komplette Küche ohne Geräte").includeAppliances()).isFalse();
    }

    @Test
    void completeWinsOverComponentWhenBoth() {
        // "cijela kuhinja s pećnicom" — a whole kitchen that happens to mention an oven → COMPLETE, not COMPONENT.
        assertThat(c.classify("cijela kuhinja s pećnicom").intent()).isEqualTo(KitchenIntent.COMPLETE);
    }

    @Test
    void wholeAndEntireAndRomanceCompleteAreAllComplete() {
        // Owner report 2026-07-18: "whole kitchen" is the SAME ask as "complete kitchen", but only the latter
        // routed to the complete-kitchen section. The class doc literally defines COMPLETE as "the user wants a
        // whole kitchen" — so the English synonyms "whole"/"entire" must route to COMPLETE too. The HR "cijela"
        // (whole) was already handled; the English side was missing its equivalent.
        assertThat(c.classify("i want a whole kitchen").intent()).isEqualTo(KitchenIntent.COMPLETE);
        assertThat(c.classify("i need the entire kitchen, budget 3000").intent()).isEqualTo(KitchenIntent.COMPLETE);
        // The qualifier stem was "kompletn" (k only), so the Romance/Dutch "completa/complete" family never
        // matched even though it is the direct translation. cucina/cocina/keuken are all kitchen words.
        assertThat(c.classify("voglio una cucina completa").intent()).isEqualTo(KitchenIntent.COMPLETE);   // IT
        assertThat(c.classify("quiero una cocina completa").intent()).isEqualTo(KitchenIntent.COMPLETE);   // ES
        assertThat(c.classify("ik wil een complete keuken").intent()).isEqualTo(KitchenIntent.COMPLETE);   // NL
    }

    @Test
    void wholeQualifierStillNeedsAKitchenWord() {
        // False-positive guard: "whole"/"entire"/"complete" WITHOUT a kitchen word must stay NONE (the qualifier is
        // only ever ANDed with a kitchen word). A whole living room is not a kitchen ask.
        assertThat(c.classify("i want to furnish the whole living room").intent()).isEqualTo(KitchenIntent.NONE);
        assertThat(c.classify("the entire bedroom on a 2000 budget").intent()).isEqualTo(KitchenIntent.NONE);
    }

    @Test
    void completeKitchenIsDetectedInEveryMarketLanguage() {
        // Audit 2026-07-18: the KITCHEN_WORD list only covered 7 languages, so the COMPLETE branch was DEAD for
        // FR/PT/NO/SE/DK/SK/FI even when the user typed the perfectly ordinary native phrasing. Each case pairs the
        // native kitchen word with a native complete/whole/fitted/furnish/modular qualifier.
        assertThat(c.classify("je veux renover ma cuisine").intent()).isEqualTo(KitchenIntent.COMPLETE);       // FR renovate
        assertThat(c.classify("une cuisine equipee sur mesure").intent()).isEqualTo(KitchenIntent.COMPLETE);   // FR fitted
        assertThat(c.classify("quero uma cozinha modular").intent()).isEqualTo(KitchenIntent.COMPLETE);        // PT modular
        assertThat(c.classify("voglio una cucina componibile").intent()).isEqualTo(KitchenIntent.COMPLETE);    // IT modular
        assertThat(c.classify("die Küche komplett einrichten").intent()).isEqualTo(KitchenIntent.COMPLETE);    // DE furnish
        assertThat(c.classify("quiero amueblar la cocina entera").intent()).isEqualTo(KitchenIntent.COMPLETE); // ES furnish
        assertThat(c.classify("quiero una cocina integral").intent()).isEqualTo(KitchenIntent.COMPLETE);       // ES built-in
        assertThat(c.classify("ik wil een volledige keuken").intent()).isEqualTo(KitchenIntent.COMPLETE);      // NL full
        assertThat(c.classify("haluan valmiin keittiön").intent()).isEqualTo(KitchenIntent.COMPLETE);          // FI ready
        assertThat(c.classify("jag vill ha ett komplett kök").intent()).isEqualTo(KitchenIntent.COMPLETE);     // SE
        assertThat(c.classify("jeg vil ha et komplett kjøkken").intent()).isEqualTo(KitchenIntent.COMPLETE);   // NO
        assertThat(c.classify("jeg vil have et komplet køkken").intent()).isEqualTo(KitchenIntent.COMPLETE);   // DK
        assertThat(c.classify("chcem kompletnú kuchyňu").intent()).isEqualTo(KitchenIntent.COMPLETE);          // SK
    }

    @Test
    void multilingualQualifierDoesNotOverTrigger() {
        // Guards for the exact over-matches the audit's adversarial pass surfaced.
        // DE "a whole new kitchen CABINET" is a single component, not a package (we deliberately did NOT add "ganze").
        assertThat(c.classify("einen ganzen neuen Küchenschrank").intent()).isEqualTo(KitchenIntent.COMPONENT);
        // FI "keittiön koko 10 neliötä" = "kitchen SIZE 10 m²" — "koko" (size) must NOT be read as a complete-qualifier.
        assertThat(c.classify("keittiön koko 10 neliötä").intent()).isNotEqualTo(KitchenIntent.COMPLETE);
        // NL "a complete set to COOK with" — "koken" (to cook) must not satisfy the kitchen word (\bkok\b is bounded).
        assertThat(c.classify("een complete set om te koken").intent()).isNotEqualTo(KitchenIntent.COMPLETE);
    }

    @Test
    void aBareCabinetOrBookcaseDoesNotFalseTriggerKitchenware() {
        // "bookcase" contains "case" but must not be read as kitchenware (glasses); a NONE prompt stays NONE.
        assertThat(c.classify("i need a bookcase for the living room").intent()).isEqualTo(KitchenIntent.NONE);
    }
}
