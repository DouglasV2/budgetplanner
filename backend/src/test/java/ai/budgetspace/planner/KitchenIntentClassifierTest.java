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
    void aBareCabinetOrBookcaseDoesNotFalseTriggerKitchenware() {
        // "bookcase" contains "case" but must not be read as kitchenware (glasses); a NONE prompt stays NONE.
        assertThat(c.classify("i need a bookcase for the living room").intent()).isEqualTo(KitchenIntent.NONE);
    }
}
