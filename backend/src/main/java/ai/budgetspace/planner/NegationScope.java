package ai.budgetspace.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sprint 10.190: works out which character ranges of a prompt sit inside a NEGATED clause, so a parser never
 * applies a preference the user explicitly ruled out ("ne želim tamno", "not cheap", "keine billigen Möbel").
 *
 * <p>Rules, in order:</p>
 * <ol>
 *   <li>The text is cut into clauses at {@code , ; . ! ?} or a CONTRAST conjunction ("ali/nego/but/aber/…").
 *       A coordinating "and" deliberately does NOT cut, so "ne zelim tamno i crno" negates both.</li>
 *   <li>A clause with any cue is negated from the end of the FIRST cue to the clause end. Note: NOT a parity
 *       count — Slavic/Romance stack negatives for emphasis ("nikako ne", "no…nada", "bez X i bez Y"), so a
 *       second cue REINFORCES rather than cancels.</li>
 *   <li>The only thing that cancels is the adjacent "negator + without" idiom ("ne bez", "nicht ohne"): everything
 *       after that {@code without}-word is a POSITIVE override, which is what turns "nicht ohne IKEA" into a
 *       preference. Two separate {@code bez} exclusions ("bez X i bez Y") are NOT adjacent, so both still apply.</li>
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
    // fires inside "nema"/"neutralno"/"nekoliko" and the English "no" never inside "not"/"north".
    private static final String CUE_SRC =
            "\\bne\\b|\\bbez\\b|\\bnemoj\\w*|\\bnecu\\b|\\bnecemo\\b|\\bnikako\\b|\\bnije\\b|\\bnisam\\b"      // HR/BS/SR
            + "|\\bbrez\\b|\\bnocem\\b"                                                                        // SI
            + "|\\bnie\\b|\\bnechcem\\b"                                                                       // SK
            + "|\\bnot\\b|\\bno\\b|\\bdont\\b|\\bdoesnt\\b|\\bwithout\\b|\\bnothing\\b|\\bnever\\b"            // EN
            + "|\\bavoid\\b|\\bskip\\b"
            + "|\\bnicht\\b|\\bkein\\w*|\\bohne\\b"                                                            // DE
            + "|\\bnon\\b|\\bsenza\\b|\\bniente\\b"                                                            // IT
            + "|\\bsin\\b|\\bnada\\b|\\bnunca\\b"                                                              // ES
            + "|\\bnao\\b|\\bsem\\b"                                                                           // PT
            // FR: "pas" is deliberately NOT a cue. French negation is discontinuous ("je NE veux PAS"), so the
            // "ne" already carries it. Leaving "pas" out also keeps "pas cher" (cheap, a POSITIVE signal) and
            // "pas trop" (the softener) clean. Cost: a colloquial "je veux pas ça" that drops the "ne" is missed.
            + "|\\bsans\\b|\\baucun\\w*|\\bjamais\\b"                                                          // FR
            + "|\\bniet\\b|\\bgeen\\b|\\bzonder\\b|\\bnooit\\b"                                                // NL
            + "|\\bei\\b|\\bala\\b|\\bilman\\b"                                                                // FI
            + "|\\binte\\b|\\bingen\\b|\\binga\\b|\\butan\\b|\\baldrig\\b"                                     // SV
            + "|\\bikke\\b|\\buten\\b|\\baldri\\b"                                                             // NO
            + "|\\buden\\b";                                                                                   // DK
    private static final Pattern CUE = Pattern.compile(CUE_SRC);

    // A negation ends at a clause break or a CONTRAST conjunction. Note: the Portuguese "mas" (but) is
    // deliberately ABSENT — it collides with the far more common Spanish "mas" (more, as in "lo mas barato"),
    // and cutting there would end a Spanish negation early. Portuguese contrast still works via the comma.
    private static final Pattern BOUNDARY = Pattern.compile(
            "[,;.!?]|\\bali\\b|\\bnego\\b|\\bvec\\b|\\bbut\\b|\\baber\\b|\\bsondern\\b|\\bma\\b|\\bpero\\b"
            + "|\\bsino\\b|\\bmais\\b|\\bmaar\\b|\\bmutta\\b|\\bmen\\b|\\bale\\b|\\bvendar\\b");

    // The "without" prepositions. A general cue immediately followed by one of these is a "not without" idiom:
    // everything after the without-word flips back to POSITIVE. These also appear in CUE (a lone "bez X" excludes).
    private static final String WITHOUT =
            "\\bbez\\b|\\bbrez\\b|\\bohne\\b|\\bsans\\b|\\bsenza\\b|\\bsin\\b|\\bsem\\b|\\butan\\b|\\buten\\b"
            + "|\\buden\\b|\\bzonder\\b|\\bilman\\b";
    // A negator directly followed by a without-word: "ne bez", "nicht ohne", "ne brez", …
    private static final Pattern CANCEL = Pattern.compile("(?:" + CUE_SRC + ")\\s+(?:" + WITHOUT + ")");

    private static final NegationScope EMPTY = new NegationScope(List.of(), List.of());

    private final List<int[]> negated;   // {startInclusive, endExclusive} — a clause is negated from its first cue
    private final List<int[]> cancelled; // {startInclusive, endExclusive} — a positive override after "negator+without"

    private NegationScope(List<int[]> negated, List<int[]> cancelled) {
        this.negated = negated;
        this.cancelled = cancelled;
    }

    static NegationScope of(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) return EMPTY;
        List<int[]> spans = new ArrayList<>();
        List<int[]> doubles = new ArrayList<>();
        for (int[] clause : clauses(normalizedText)) {
            String segment = normalizedText.substring(clause[0], clause[1]);
            Matcher cue = CUE.matcher(segment);
            if (cue.find()) {
                // any cue negates from the end of the FIRST one to the clause end (reinforcement, not parity)
                spans.add(new int[]{clause[0] + cue.end(), clause[1]});
            }
            // each "negator + without" flips everything after the without-word back to positive
            Matcher cancel = CANCEL.matcher(segment);
            while (cancel.find()) {
                doubles.add(new int[]{clause[0] + cancel.end(), clause[1]});
            }
        }
        return spans.isEmpty() && doubles.isEmpty() ? EMPTY : new NegationScope(spans, doubles);
    }

    /** True when a match STARTING at this offset is negated — inside a negated clause and NOT in a "not without"
     * positive override. */
    boolean isNegated(int matchStart) {
        return within(negated, matchStart) && !within(cancelled, matchStart);
    }

    /**
     * True when this offset sits in a clause whose negations CANCEL OUT ("nicht ohne IKEA", "ne bez tepiha").
     * {@link #isNegated} cannot express this — it returns false both for "no negation at all" and for "negated
     * twice" — so a caller that owns its own negative trigger (the retailer exclude window) asks this instead,
     * and turns the exclusion into a preference.
     */
    boolean isDoubleNegated(int matchStart) {
        return within(cancelled, matchStart);
    }

    private static boolean within(List<int[]> ranges, int position) {
        for (int[] range : ranges) {
            if (position >= range[0] && position < range[1]) return true;
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
