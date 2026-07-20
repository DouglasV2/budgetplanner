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
    void frenchDiscontinuousNegationCountsOnce() {
        // "ne ... pas" is ONE negation written with two words — counting both would cancel it out.
        assertThat(negated("je ne veux pas de moderne", "moderne")).isTrue();
        assertThat(negated("je ne veux pas quelque chose de cher", "cher")).isTrue();
        // ...while a bare "pas cher" (cheap) is a positive price signal, not a negation.
        assertThat(negated("meubler pas cher le salon", "salon")).isFalse();
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
    void reportsACancelledNegationSeparatelyFromNoNegationAtAll() {
        // isNegated() is false in BOTH cases, so a caller that owns its own negative trigger (the retailer
        // exclude window) needs this second signal to tell "nicht ohne IKEA" from a plain "IKEA".
        String cancelled = "nicht ohne ikea";
        assertThat(NegationScope.of(cancelled).isNegated(at(cancelled, "ikea"))).isFalse();
        assertThat(NegationScope.of(cancelled).isDoubleNegated(at(cancelled, "ikea"))).isTrue();

        String plain = "samo ikea";
        assertThat(NegationScope.of(plain).isDoubleNegated(at(plain, "ikea"))).isFalse();

        String single = "ohne ikea";
        assertThat(NegationScope.of(single).isDoubleNegated(at(single, "ikea"))).isFalse();
    }

    @Test
    void emptyAndNullAreSafe() {
        assertThat(NegationScope.of("").isNegated(0)).isFalse();
        assertThat(NegationScope.of(null).isNegated(0)).isFalse();
        assertThat(NegationScope.of("   ").isNegated(0)).isFalse();
    }
}
