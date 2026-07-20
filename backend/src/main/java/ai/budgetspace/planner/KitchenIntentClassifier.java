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

    // "This is about a kitchen at all." Multilingual across all 15 markets — kept in parity with
    // PlannerIntentExtractor.applyRoom's kitchen list, which was already broad while THIS list lagged (only 7
    // languages), so the whole COMPLETE branch was dead for FR/PT/NO/SE/DK/SK/FI. normalize() now folds the
    // Nordic ligatures too, so the Scandinavian words are written in their ASCII form (kjokken/kokken/kok).
    private static final Pattern KITCHEN_WORD = Pattern.compile(
        "(kuhinj|kuchyn|kuchen|kuche|kitchen|cucina|cocina|cuisine|cozinh|keuken|keitti|\\bkok\\b|\\bkoket|\\bkoks|kjokken|kokken)");
    // A "whole/complete/fitted/modular/renovate/furnish" qualifier. Checked ANYWHERE in the prompt together with a
    // kitchen word (AND), so word order ("kompletna L kuhinja") doesn't matter. "modern" is deliberately NOT here —
    // a plain "moderna kuhinja" is today's freestanding behaviour, only an explicit complete-ask shows sets.
    //
    // Owner report 2026-07-18: "whole kitchen" is the SAME ask as "complete kitchen" but only the latter routed here,
    // because the qualifier had the HR "cijel" (whole) but no English "whole"/"entire", and the "complete" stem was
    // k-only ("kompletn"), so the Romance/Dutch "complet-" family (IT "completa", ES "cocina completa", FR "complète",
    // NL "complete keuken") never matched its own direct translation. "komplet"/"complet" now cover both spellings
    // (kompletn/komplett/kompletná/completa/complete/complète); "\bwhole\b|\bentire\b" adds the English whole-word
    // synonyms (word-bounded so "wholesale"/"entirely" don't trip; still ANDed with a kitchen word, so a "whole
    // living room" stays out of scope here).
    // Multilingual complete-qualifier stems (audit 2026-07-18): "componibil" IT (modular), "equipee"/"amenag" FR
    // (fitted/laid-out), "einricht" DE (furnish), "valmis" FI (ready/complete), "amuebl" ES (furnish), "\bintegral"
    // ES (cocina integral = built-in), "volledig" NL (complete/full). Deliberately NOT included: "\bganze" DE (would
    // wrongly flag "ganzen neuen Küchenschrank" = a whole new cabinet, a COMPONENT) and FI "koko" (means "size" in
    // "keittiön koko 10 m²"). Each is ANDed with a kitchen word, and the complete-kitchen section is browse-only, so
    // a rare over-trigger only shows an extra sets panel, never alters the main plan.
    private static final Pattern COMPLETE_QUALIFIER = Pattern.compile(
        "(komplet|complet|cijel|\\bwhole\\b|\\bentire\\b|modularn|modular|componibil|einbau|kuchenzeile|modulkuch"
        + "|fitted|equipee|amenag|einricht|\\bvalmis\\b|\\bvalmiin|amuebl|\\bintegral|volledig|renov|oprem|uredi|opremi"
        + "|furnish|u paketu)");
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
        // NFD strips ž/š/č/ć/ä/ö/é etc.; the Nordic/German ligatures æ ø å ß are NOT decomposed by NFD, so fold
        // them explicitly (mirrors PlannerIntentExtractor.normalize) — otherwise a Norwegian "kjøkken", Danish
        // "køkken" or Swedish "kök" never reduced to their ASCII kitchen tokens and the COMPLETE branch stayed dead.
        return java.text.Normalizer.normalize(lowered, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace("æ", "ae").replace("ø", "o").replace("å", "a").replace("ß", "ss");
    }
}
