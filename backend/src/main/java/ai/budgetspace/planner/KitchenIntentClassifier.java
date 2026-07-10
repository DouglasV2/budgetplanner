package ai.budgetspace.planner;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sprint 10.175 (kitchen Increment 1): deterministic, multilingual kitchen-intent classifier. AI is off by
 * default, so this rule-based path is the one that runs.
 *
 * <ul>
 *   <li>COMPLETE — the user wants a whole kitchen (planned/bought as a package).</li>
 *   <li>COMPONENT — an individual kitchen part (a dishwasher, a cabinet, a worktop…).</li>
 *   <li>KITCHENWARE — pots/plates/cutlery/food-storage (secondary; never auto-injected).</li>
 *   <li>NONE — not a kitchen ask.</li>
 * </ul>
 *
 * Only COMPLETE is acted on this increment; COMPONENT/KITCHENWARE are recognised (so routing is real and
 * tested) but fall through to today's behaviour. Diacritics are stripped before matching, so the patterns
 * below are plain ASCII.
 */
public class KitchenIntentClassifier {
    public enum KitchenIntent { COMPLETE, COMPONENT, KITCHENWARE, NONE }
    public enum KitchenShape { SINGLE_WALL, L_SHAPED, U_SHAPED, GALLEY, ISLAND, UNKNOWN }
    public record KitchenBrief(KitchenIntent intent, KitchenShape shape, boolean includeAppliances) {}

    // "This is about a kitchen at all."
    private static final Pattern KITCHEN_WORD = Pattern.compile("(kuhinj|kuchen|kuche|kitchen|cucina|cocina|keuken)");
    // A "whole/complete/fitted/modular/renovate/furnish" qualifier. Checked ANYWHERE in the prompt together with a
    // kitchen word (AND), so word order ("kompletna L kuhinja") doesn't matter. "modern" is deliberately NOT here —
    // a plain "moderna kuhinja" is today's freestanding behaviour, only an explicit complete-ask shows sets.
    private static final Pattern COMPLETE_QUALIFIER = Pattern.compile(
        "(kompletn|cijel|modularn|modular|komplett|einbau|kuchenzeile|modulkuch|fitted|complete kitchen"
        + "|renov|oprem|uredi|opremi|furnish|u paketu)");
    // Unambiguous complete-kitchen product line (only ever a kitchen), safe on its own.
    private static final Pattern KNOXHULT = Pattern.compile("knoxhult");

    // COMPONENT: an individual kitchen part (HR/DE/EN seed set).
    private static final Pattern COMPONENT = Pattern.compile(
        "(perilic\\w* posud|sudoper|slavin|pecnic|ploc\\w* za kuhanj|napa|hladnjak|zamrziva|kuhinjsk\\w* ormaric|ladic|radn\\w* ploc"
        + "|geschirrspul|backofen|kochfeld|dunstabzug|kuhlschrank|gefrierschrank|spule|arbeitsplatt|kuchenschrank"
        + "|dishwasher|\\boven\\b|\\bhob\\b|cooktop|extractor hood|fridge|refrigerator|freezer|kitchen sink|kitchen cabinet|worktop|countertop)");

    // KITCHENWARE: pots/plates/etc. Short/ambiguous tokens are word-boundary-guarded so "bookcase" (→case) can't
    // false-trigger. Secondary — checked last.
    private static final Pattern KITCHENWARE = Pattern.compile(
        "(\\bposud|tanjur|zdjel|pribor za jelo|\\bcase\\b|\\bsalic|\\blonac|\\btava\\b|cookware|tableware|cutlery"
        + "|\\bplates\\b|\\bbowls\\b|\\bglasses\\b|\\bmugs\\b|utensil)");

    private static final Pattern APPLIANCES_YES = Pattern.compile("(s uredaj|sa uredaj|s aparat|mit gerat|with appliance|incl\\w* appliance)");
    private static final Pattern APPLIANCES_NO = Pattern.compile("(bez uredaj|bez aparat|ohne gerat|without appliance|no appliance)");

    public KitchenBrief classify(String prompt) {
        String p = normalize(prompt);
        KitchenIntent intent;
        if (KNOXHULT.matcher(p).find() || (KITCHEN_WORD.matcher(p).find() && COMPLETE_QUALIFIER.matcher(p).find())) {
            intent = KitchenIntent.COMPLETE;                 // whole-kitchen phrasing wins even if a part is named
        } else if (COMPONENT.matcher(p).find()) {
            intent = KitchenIntent.COMPONENT;
        } else if (KITCHENWARE.matcher(p).find()) {
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
        return APPLIANCES_YES.matcher(p).find();   // default false unless the user explicitly asks for appliances
    }

    /**
     * Lowercase + strip diacritics. NOTE: the Croatian "đ"/"Đ" (U+0111/U+0110) is a stroked letter with NO
     * canonical (NFD) decomposition, so it must be replaced explicitly — otherwise it survives normalization and
     * "posuđa"/"uređaj" never match "posud"/"uredaj". (č/ć/š/ž DO decompose to base + combining mark.)
     */
    private String normalize(String prompt) {
        if (prompt == null) return "";
        String lowered = prompt.toLowerCase(Locale.ROOT).replace('đ', 'd').replace('ð', 'd');
        return java.text.Normalizer.normalize(lowered, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
