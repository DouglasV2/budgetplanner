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
    // fires inside "nema"/"neutralno"/"nekoliko" and the English "no" never inside "not"/"north".
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
