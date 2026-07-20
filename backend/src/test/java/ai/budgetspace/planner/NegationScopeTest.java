package ai.budgetspace.planner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10.190: the clause-scoped negation detector. Inputs here are already in the normalized form the
 * parsers use (lower-case, accent-stripped, Nordic ligatures folded), so every string below is plain ASCII.
 */
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
        assertThat(negated("ne zelim tamno, hocu svijetlo", "svijetlo")).isFalse();
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
    void aTokenThatContainsTheCueIsNotItselfNegated() {
        // Our own positive signals that happen to be negative-shaped must survive.
        assertThat(negated("meubler pas cher", "pas cher")).isFalse();
        assertThat(negated("ne vise od dvije trgovine", "ne vise od dvije")).isFalse();
        assertThat(negated("bez puno obilazaka", "bez puno obilazaka")).isFalse();
    }

    @Test
    void shortCuesDoNotFireInsideLongerWords() {
        // HR "ne" must not match inside nema / neutralno / nekoliko.
        assertThat(negated("nemam nista neutralno i nekoliko polica", "polica")).isFalse();
        // A cue AFTER the word leaves the word alone.
        assertThat(negated("wohnzimmer keine ahnung", "wohnzimmer")).isFalse();
    }

    @Test
    void emptyAndNullAreSafe() {
        assertThat(NegationScope.of("").isNegated(0)).isFalse();
        assertThat(NegationScope.of(null).isNegated(0)).isFalse();
        assertThat(NegationScope.of("   ").isNegated(0)).isFalse();
    }
}
