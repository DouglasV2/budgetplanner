package ai.budgetspace.planner;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sprint 10.181 — centralized, reusable budget/amount parsing for the deterministic intent path.
 *
 * <p>Extracted out of {@link PlannerIntentExtractor} so the rule-based fallback understands how real users
 * actually write money, and so the logic is unit-testable in isolation. It resolves, in priority order:</p>
 * <ol>
 *   <li><b>Explicit</b> amounts — a digit run carrying a currency ({@code 1000 €}, {@code €1000}, {@code 800e},
 *       {@code £1800}), a budget verb ({@code do 1000}, {@code oko 1500}, {@code maks 800}), a European grouped
 *       number ({@code 1.000}, {@code 1 000}), or a {@code k} shorthand ({@code 1k}, {@code 1.5k}, {@code 1,5k}).</li>
 *   <li><b>Number-words / regional slang</b> — {@code soma}=1000, {@code dva soma}=2000, {@code soma i po}=1500,
 *       {@code tisuću}/{@code hiljadu} and multiples, {@code pola soma}=500.</li>
 *   <li><b>A bare standalone number</b> ({@code boravak 1000}) — only when it is not glued to a size/quantity unit,
 *       not a near-future year, and in a sane budget range, so a room size ({@code 20 m2}), a quantity
 *       ({@code 6 stolica}), a phone/order number or a year ({@code 2026}) never becomes a budget.</li>
 * </ol>
 *
 * <p>Input MUST already be lower-cased and accent-stripped (the {@code PlannerIntentExtractor.normalize} form:
 * ž→z, š→s, č→c, ć→c, đ→d). Returns the amount as a plain integer in the market's own currency units — no
 * currency conversion is ever performed. Never throws on hostile input.</p>
 */
final class AmountParser {

    private AmountParser() {
    }

    // A plain integer or a grouped one — European dot/space (1.000 / 1 000) OR English/US comma (1,500). 3–6
    // significant digits after grouping. digits() strips all three separators before parsing.
    private static final String NUMBER = "(\\d{1,3}(?:[.,\\s]\\d{3})+|\\d{3,6})";
    private static final String CURRENCY =
            "(?:€|eur|eura|euro|kr|kroner|kronor|nok|sek|dkk|kn|kuna|gbp|pund|pounds|quid|£|\\$)";
    private static final String VERBS =
            "(?:budget|budzet|budjet|do|ispod|maksimalno|maks|max|maximum|maximal|najvise|ne preko|ne vise od|imam|oko"
            + "|otprilike|around|about|approx|circa|cirka|\\bca\\b|etwa|ungefahr|environ|jusqu|fino a|hasta|alrededor|\\bate\\b"
            + "|omkring|runt|noin|under|up to|bis|za)";

    // 1k / 1.5k / 1,5k → ×1000. The k must follow the digits and end a word so "kuhinja" is never caught.
    private static final Pattern K_SUFFIX = Pattern.compile("(?<![a-z0-9])(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*k(?![a-z0-9])");
    // 800e / 1000e — a bare "e" glued to a digit run is the euro shorthand (not a full "eur").
    private static final Pattern E_SUFFIX = Pattern.compile("(?<![\\d.,])(\\d{3,6})e(?![a-z0-9])");
    // A size/quantity unit right after a number means it is NOT money. Audit 2026-07-20: added cm/mm (a furniture
    // dimension like "180 cm" was being read as a €180 budget) and % (a "100% pamuk" / "100% cotton" material note
    // was read as a €100 budget).
    private static final String UNIT_AFTER = "\\s*(?:m2|m²|kvadrat|kom\\b|komad|puta|\\bx\\b|osob|sjedal|godin|h\\b|cm\\b|mm\\b|%|posto\\b|posti\\b|percent|prosent|procent|pourcent|prozent|pct\\b|:)";
    private static final Pattern BARE = Pattern.compile("(?<![\\d.,€$£])\\b(\\d{3,6})\\b(?!" + UNIT_AFTER + ")(?![.,]\\d)");

    // Number × thousand-word (audit 2026-07-20): "10 mila €" (IT), "5 tusen kr" (NO/SE), "5 tusind" (DK), "2 tisic"
    // (SK), "2 tisoc" (SI), "3 mil" (ES/PT). A leading digit run is REQUIRED, so the bare words never fire on
    // "tusen takk" (NO "thanks a lot"), the name "Mila", or "mil" inside "million"/"família".
    private static final Pattern MULT_THOUSAND =
            Pattern.compile("(?<![\\d.,])(\\d{1,3})\\s*(?:tusen|tusind|tisic|tisoc|mila|mil)\\b");

    private static final List<Pattern> EXPLICIT = List.of(
            Pattern.compile(NUMBER + "\\s*" + CURRENCY),          // 1000 €, 1.000 eur, 1 000 kr
            Pattern.compile("(?:£|\\$|€)\\s*" + NUMBER),           // €1000, £1800, $1200
            Pattern.compile(VERBS + "\\s*" + NUMBER)               // do 1000, oko 1500, maks 800, za 1000
    );

    /** The leftmost, most trustworthy budget in the text, or empty. Amount is in the market's own currency. */
    static java.util.Optional<Integer> parseBudget(String text) {
        if (text == null || text.isBlank()) return java.util.Optional.empty();

        int bestIndex = Integer.MAX_VALUE;
        Integer bestValue = null;

        // 1) explicit currency / verb / grouped
        for (Pattern pattern : EXPLICIT) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                Integer parsed = sane(digits(m.group(1)));
                if (parsed != null && m.start() < bestIndex) { bestIndex = m.start(); bestValue = parsed; }
            }
        }
        // 2) k-suffix
        Matcher k = K_SUFFIX.matcher(text);
        while (k.find()) {
            Integer parsed = fromK(k.group(1));
            if (parsed != null && k.start() < bestIndex) { bestIndex = k.start(); bestValue = parsed; }
        }
        // 3) e-suffix
        Matcher e = E_SUFFIX.matcher(text);
        while (e.find()) {
            Integer parsed = sane(digits(e.group(1)));
            if (parsed != null && e.start() < bestIndex) { bestIndex = e.start(); bestValue = parsed; }
        }
        // 3b) number × thousand-word (10 mila, 5 tusen, 2 tisic)
        Matcher mt = MULT_THOUSAND.matcher(text);
        while (mt.find()) {
            Integer parsed = sane(Long.parseLong(mt.group(1)) * 1000L);
            if (parsed != null && mt.start() < bestIndex) { bestIndex = mt.start(); bestValue = parsed; }
        }
        // 4) number-words / slang
        int[] word = parseNumberWord(text);
        if (word != null && word[0] < bestIndex) { bestIndex = word[0]; bestValue = word[1]; }

        if (bestValue != null) return java.util.Optional.of(bestValue);

        // 5) bare standalone number — only when nothing explicit was found (lowest trust)
        Matcher b = BARE.matcher(text);
        while (b.find()) {
            String raw = b.group(1);
            Integer parsed = sane(digits(raw));
            if (parsed == null) continue;
            if (isYearLike(parsed, raw, text, b.start())) continue; // "u 2026" / "iz 2018" is a year, not a budget
            // Sprint 10.190: a leading zero, or a group flanked by another 2+ digit group, is a phone/area/order
            // number, not money — "zovite 095 123 456" must not become a €95 (or €123) budget.
            if (raw.startsWith("0")) continue;
            String pre = text.substring(Math.max(0, b.start() - 6), b.start());
            String post = text.substring(b.end(), Math.min(text.length(), b.end() + 6));
            if (pre.matches(".*\\d{2,}[\\s\\-/]$") || post.matches("^[\\s\\-/]\\d{2,}.*")) continue;
            return java.util.Optional.of(parsed);
        }
        return java.util.Optional.empty();
    }

    // --- number-words (Croatian / Bosnian / Serbian colloquial + slang) ---

    // A thousand-scale base word: "som/soma" (slang), "tisuc..." , "hiljad..." . Group the leading multiplier and
    // an optional trailing "i po/pol" (=+half). Also "pol(a) <base>" = half.
    private static final Pattern NUM_WORD = Pattern.compile(
            "(?:\\b(pol[ao]?)\\s+)?"
            + "(?:\\b(jedan|jednu|jedna|dva|dvije|tri|cetiri|pet|sest|sedam|osam|devet|deset|\\d{1,2})\\s+)?"
            + "\\b(som(?:a|ova|ove|u|ovima)?|tisu(?:c|ca|cu|ce)a?|hiljad(?:a|u|e))\\b"
            + "(\\s+i\\s+(?:po|pol)|\\s+ipol?)?");

    private static int[] parseNumberWord(String text) {
        Matcher m = NUM_WORD.matcher(text);
        while (m.find()) {
            String half = m.group(1);
            String mult = m.group(2);
            String andHalf = m.group(4);
            long base = 1000; // som/tisuca/hiljada all == one thousand
            long value;
            if (half != null) {
                value = base / 2;                 // "pola soma" = 500
            } else {
                long n = multiplier(mult);
                value = n * base;                 // "dva soma" = 2000, "soma" = 1000
                if (andHalf != null) value += base / 2; // "soma i po" = 1500
            }
            Integer sane = sane(value);
            if (sane != null) return new int[]{m.start(), sane};
        }
        return null;
    }

    private static long multiplier(String word) {
        if (word == null) return 1;
        if (word.matches("\\d{1,2}")) return Long.parseLong(word); // "2 soma" = 2000, "12 tisuca" = 12000
        return switch (word) {
            case "jedan", "jednu", "jedna" -> 1;
            case "dva", "dvije" -> 2;
            case "tri" -> 3;
            case "cetiri" -> 4;
            case "pet" -> 5;
            case "sest" -> 6;
            case "sedam" -> 7;
            case "osam" -> 8;
            case "devet" -> 9;
            case "deset" -> 10;
            default -> 1;
        };
    }

    // --- helpers ---

    private static String digits(String grouped) {
        return grouped == null ? "" : grouped.replaceAll("[.,\\s]", "");
    }

    private static Integer fromK(String num) {
        try {
            double v = Double.parseDouble(num.replace(',', '.'));
            long amount = Math.round(v * 1000);
            return sane(amount);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    /** In [1, 100_000_000] (covers high-denomination NOK/SEK) or null. Never throws. */
    private static Integer sane(String raw) {
        try {
            return sane(Long.parseLong(raw));
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    private static Integer sane(long value) {
        return (value >= 1 && value <= 100_000_000L) ? (int) value : null;
    }

    /**
     * A bare 4-digit number that is really a YEAR, not a budget. 2020-2099 is unconditionally a year (a near-future
     * date). Sprint 10.190: 1990-2019 collides with very common real budgets ("boravak 2000"), so those count as a
     * year ONLY when a year-preposition sits right before them ("iz 2018", "od 2015", "godine 2019").
     */
    private static boolean isYearLike(int value, String raw, String text, int matchStart) {
        if (raw.length() != 4 || value < 1990 || value > 2099) return false;
        if (value >= 2020) return true;
        String before = text.substring(Math.max(0, matchStart - 12), matchStart);
        return before.matches(".*\\b(?:iz|od|godin\\w*|leta|from|since|year|jahr|\\bab|anno|del)\\s*$");
    }
}
