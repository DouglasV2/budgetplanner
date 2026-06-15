package ai.budgetspace.planner;

import ai.budgetspace.dto.PlannerInputDto;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a plain sentence into structured planner choices, without an LLM.
 *
 * <p>This is the centralised, testable replacement for the parsing that used to
 * live inside {@code PlannerService}. The important behaviours:</p>
 * <ul>
 *   <li>"Imam 1500 €" sets the budget and never marks a product as already owned —
 *       only phrases like "već imam", "imam …" (followed by a thing, not a number),
 *       "ne treba mi", "bez …" and "ne dodaj …" remove a category.</li>
 *   <li>Wishes ("treba mi …", "obavezno …", "najvažniji mi je …") add a category and
 *       give it priority.</li>
 *   <li>Categories stay precise: "stolica" → chair, "stolić" → table, "radni stol" →
 *       desk, "stol" alone is left ambiguous (never chair).</li>
 *   <li>Store wishes ("jedna trgovina", "ne više od dvije trgovine") and retailer
 *       wishes ("najviše IKEA", "bez Lesnine") are picked up.</li>
 * </ul>
 */
public class PlannerIntentExtractor {

    private static final Map<String, String> RETAILER_STEMS = retailerStems();

    // Category text patterns. Order is not important because each is checked independently,
    // but precision is: "stolic(?!a)" never swallows "stolica", and storage "komoda" is
    // ignored when it is part of "tv komoda".
    private static final Map<String, Pattern> CATEGORY_PATTERNS = Map.ofEntries(
            Map.entry("sofa", Pattern.compile("kauc|sofa|trosjed|dvosjed|garnitur|couch")),
            Map.entry("tv-unit", Pattern.compile("tv komod|komoda za tv|tv element|tv unit|\\btv\\b|televizor")),
            Map.entry("table", Pattern.compile("\\bstolic\\b|klub stol|coffee table")),
            Map.entry("rug", Pattern.compile("tepih|\\bsag\\b")),
            Map.entry("lighting", Pattern.compile("lamp|rasvjet|svjetilj|svjetlo")),
            Map.entry("storage", Pattern.compile("polic|regal|ormar|spremanje|(?<!tv )komod")),
            Map.entry("decor", Pattern.compile("dekor|ukras|slik|jastuk|biljk|svijec")),
            Map.entry("desk", Pattern.compile("radni stol|pisaci stol|\\bdesk\\b")),
            Map.entry("chair", Pattern.compile("\\bstolica\\b|\\bstolice\\b|\\bstolicu\\b|\\bstolicom\\b|fotelj|\\bchair\\b")),
            Map.entry("bed", Pattern.compile("krevet|\\bbed\\b")),
            Map.entry("mattress", Pattern.compile("madrac|mattress")),
            Map.entry("gym-equipment", Pattern.compile("bucic|utez|girja|klup|bench|sprava|traka za trcanje|bicikl|oprema za vjezb")),
            // Sprint 10.7: new-room categories. These are precise multi-word phrases so they do not
            // collide with the single-word kitchen/bedroom synonyms above.
            Map.entry("dining-table", Pattern.compile("blagovaonski stol|trpezarijski stol|stol za blagovanje|dining table")),
            Map.entry("dining-chair", Pattern.compile("blagovaonsk[ae] stolic|trpezarijsk[ae] stolic|dining chair")),
            Map.entry("kitchen-storage", Pattern.compile("kuhinjski ormar|kuhinjska polic|kuhinjsko spreman|kitchen storage")),
            Map.entry("kitchen-cart", Pattern.compile("kuhinjska kolica|servirna kolica|kitchen cart")),
            Map.entry("nightstand", Pattern.compile("nocni ormar|nightstand")),
            Map.entry("wardrobe", Pattern.compile("ormar za odjec|garderobni ormar|plakar|wardrobe")),
            Map.entry("dresser", Pattern.compile("komoda s ladic|ladicar|dresser"))
    );

    // Sprint 10.7: colour and material preferences. Keys are the canonical tags shared with
    // ProductTaxonomy.deriveColorTags / deriveMaterialTags, so a parsed preference can be matched
    // directly against a product's colorTags / materialTags. Patterns run over accent-free text.
    private static final Map<String, Pattern> COLOR_PATTERNS = colorPatterns();
    private static final Map<String, Pattern> MATERIAL_PATTERNS = materialPatterns();

    // Group 1 marks an "already have / exclude" clause, group 2 marks a "need" clause.
    // Longer and negated phrases come first so "ne treba mi" wins over "treba".
    private static final Pattern CLAUSE_TRIGGER = Pattern.compile(
            "(vec imam|imam vec|imam doma|imam kod kuce|ne treba mi|ne trebam|ne treba|ne dodavaj|ne dodaj|ne zelim|bez|preskoci|maknuti|makni|imam(?!\\s*\\d))"
                    + "|(treba mi|trebam|treba|fali mi|fali|dodaj|zelim|obavezno|prioritet|najvazniji|najvazni|najvise mi|volio bih|voljela bih)");

    public PlannerInputDto enrich(PlannerInputDto rawInput) {
        PlannerInputDto input = rawInput == null ? defaults() : rawInput.normalized();
        String text = normalize(input.prompt());
        if (text.isBlank()) {
            return input;
        }

        Optional<Integer> budget = findBudget(text);
        if (budget.isPresent()) input = input.withBudget(clamp(budget.get(), 100, 9000));

        Optional<Integer> size = firstNumber(text, Pattern.compile("(\\d{1,2})\\s*(m2|m²|kvadrat)"));
        if (size.isPresent()) input = input.withSize(clamp(size.get(), 8, 60));

        input = applyRoom(text, input);
        input = applyStyle(text, input);
        input = applyFurnishingLevel(text, input);
        input = applyOptimizationGoal(text, input);
        input = applyRetailerIntent(text, input);
        input = applyCategories(text, input);
        input = applyColorAndMaterialPreferences(text, input);

        return input;
    }

    private PlannerInputDto applyRoom(String text, PlannerInputDto input) {
        if (matches(text, "dnevn|boravak|living")) input = input.withRoomType("living-room");
        if (matches(text, "radni kutak|radni prostor|home office|\\bured\\b|office|posao")) input = input.withRoomType("home-office");
        if (matches(text, "spava|bedroom|spavac")) input = input.withRoomType("bedroom");
        if (matches(text, "teretan|gym|trening|fitness")) input = input.withRoomType("home-gym");
        // Sprint 10.7: new rooms. Checked after the originals, so the last room mentioned wins.
        if (matches(text, "kuhinj|kitchen")) input = input.withRoomType("kitchen");
        if (matches(text, "blagovaon|trpezarij|dining")) input = input.withRoomType("dining-room");
        if (matches(text, "hodnik|predsoblje|hallway")) input = input.withRoomType("hallway");
        if (matches(text, "kupaon|kupatil|bathroom")) input = input.withRoomType("bathroom");
        return input;
    }

    private PlannerInputDto applyStyle(String text, PlannerInputDto input) {
        if (matches(text, "ne znam|svejedno|predlozi")) input = input.withStyle("surprise");
        if (matches(text, "svijetl|prozrac|skandi|scandi|nordic|skandinav")) input = input.withStyle("bright");
        if (matches(text, "toplo|ugodno|mekano|domac|cozy")) input = input.withStyle("warm");
        if (matches(text, "modern|uredno")) input = input.withStyle("modern");
        if (matches(text, "minimal|jednostavn|cisto")) input = input.withStyle("minimal");
        if (matches(text, "classic|klasic|klasc")) input = input.withStyle("classic");
        if (matches(text, "industrial|industrij|tamno|crno|metal")) input = input.withStyle("industrial");
        if (matches(text, "boho|prirodn|biljk|ratan|natural")) input = input.withStyle("boho");
        return input;
    }

    private PlannerInputDto applyFurnishingLevel(String text, PlannerInputDto input) {
        if (matches(text, "osnovno|samo osnov|minimalno oprem|samo najvaznij")) input = input.withFurnishingLevel("basic");
        if (matches(text, "udobnij|normalno|dovoljno komplet")) input = input.withFurnishingLevel("comfort");
        if (matches(text, "kompletno|sve oprem|opremi sve|\\bfull\\b|dovrsen|odmah gotovo")) input = input.withFurnishingLevel("complete");
        return input;
    }

    private PlannerInputDto applyOptimizationGoal(String text, PlannerInputDto input) {
        if (matches(text, "najjeftin|sto jeftin|low cost|jeftino|budget")) input = input.withOptimizationGoal("lowest-price");
        if (matches(text, "best value|omjer|balans|vrijednost")) input = input.withOptimizationGoal("best-value");
        if (matches(text, "najljep|estetsk|ljepsa verzij|sto ljepse")) input = input.withOptimizationGoal("style-match");
        return input;
    }

    private PlannerInputDto applyRetailerIntent(String text, PlannerInputDto input) {
        LinkedHashSet<String> preferred = new LinkedHashSet<>();
        LinkedHashSet<String> excluded = new LinkedHashSet<>();
        List<String> mentioned = new ArrayList<>();

        for (Map.Entry<String, String> entry : RETAILER_STEMS.entrySet()) {
            String retailer = entry.getKey();
            String stem = entry.getValue();
            int idx = text.indexOf(stem);
            if (idx < 0) continue;
            mentioned.add(retailer);
            String before = text.substring(Math.max(0, idx - 22), idx);
            if (matches(before, "\\bbez\\b|izbjegni|izbaci|ne zelim|ne trebam|ne treba|\\bosim\\b|preskoci")) {
                excluded.add(retailer);
            } else if (matches(before, "najvise|radije|preferiram|ako moze|po mogucnosti|volio bih|voljela bih|prvenstveno|preferira")) {
                preferred.add(retailer);
            }
        }

        // Store limit: explicit numbers win over the soft "fewer stores" wish.
        int maxStores = 0;
        if (matches(text, "jedna trgovina|jednu trgovinu|samo jedna|iz jedne trgovine|jedan odlazak|sve iz jedne")) {
            maxStores = 1;
        } else if (matches(text, "dvije trgovine|dvije trgovina|maksimalno dvije|maks dvije|ne vise od dvije|do dvije trgovine|najvise dvije|dvije trgovin")) {
            maxStores = 2;
        } else if (matches(text, "ne zelim puno trgovina|sto manje trgovina|manje trgovina|bez puno obilazaka|bez obilazaka|manje obilazaka|ne zelim obilaziti|bez puno trgovina")) {
            maxStores = 2;
            if ("best-value".equals(input.optimizationGoal())) input = input.withOptimizationGoal("least-stores");
        }

        List<String> allRetailers = defaults().selectedRetailers();
        List<String> baseSelected = input.selectedRetailers() == null || input.selectedRetailers().isEmpty()
                ? allRetailers
                : input.selectedRetailers();

        String retailerMode = input.retailerMode();
        List<String> selected = new ArrayList<>(baseSelected);

        boolean explicitSingle = mentioned.size() == 1 && matches(text, "samo|iskljucivo|sve iz|jedino");
        if (explicitSingle) {
            String only = mentioned.get(0);
            if (!excluded.contains(only)) {
                retailerMode = "single";
                selected = List.of(only);
            }
        }

        if (!excluded.isEmpty() && !explicitSingle) {
            selected = new ArrayList<>(allRetailers);
            selected.removeAll(excluded);
            // Keep within what the user (or form) already allowed, if that was a subset.
            if (input.selectedRetailers() != null && !input.selectedRetailers().isEmpty()
                    && input.selectedRetailers().size() < allRetailers.size()) {
                List<String> intersect = new ArrayList<>(baseSelected);
                intersect.removeAll(excluded);
                if (!intersect.isEmpty()) selected = intersect;
            }
            if (selected.isEmpty()) {
                selected = new ArrayList<>(allRetailers);
                selected.removeAll(excluded);
            }
        }

        return input.withRetailerIntent(retailerMode, selected, new ArrayList<>(preferred), new ArrayList<>(excluded), maxStores);
    }

    private PlannerInputDto applyCategories(String text, PlannerInputDto input) {
        LinkedHashSet<String> alreadyHave = new LinkedHashSet<>(input.alreadyHaveCategories());
        LinkedHashSet<String> mustHave = new LinkedHashSet<>(input.mustHaveCategories());

        Matcher matcher = CLAUSE_TRIGGER.matcher(text);
        List<int[]> markers = new ArrayList<>(); // {start, end, type} type 0 = have, 1 = need
        while (matcher.find()) {
            int type = matcher.group(1) != null ? 0 : 1;
            markers.add(new int[]{matcher.start(), matcher.end(), type});
        }

        for (int i = 0; i < markers.size(); i++) {
            int[] marker = markers.get(i);
            int segmentStart = marker[1];
            int segmentEnd = i + 1 < markers.size() ? markers.get(i + 1)[0] : text.length();
            if (segmentEnd <= segmentStart) continue;
            String segment = text.substring(segmentStart, segmentEnd);
            for (String category : categoriesIn(segment)) {
                if (marker[2] == 0) alreadyHave.add(category);
                else mustHave.add(category);
            }
        }

        // A category the user explicitly asked for wins over an ambiguous "already have".
        // e.g. "već imam TV ... treba mi TV komoda" -> tv-unit stays requested.
        alreadyHave.removeAll(mustHave);

        return input.withCategories(new ArrayList<>(mustHave), new ArrayList<>(alreadyHave));
    }

    private List<String> categoriesIn(String segment) {
        List<String> found = new ArrayList<>();
        CATEGORY_PATTERNS.forEach((category, pattern) -> {
            if (pattern.matcher(segment).find()) found.add(category);
        });
        return found;
    }

    // Colour/material preferences are read from the whole sentence (not from need/have clauses):
    // "zidovi u zelenoj boji, drvo i crni detalji" -> colors {green, black}, materials {wood}.
    private PlannerInputDto applyColorAndMaterialPreferences(String text, PlannerInputDto input) {
        List<String> colors = matchKeys(text, COLOR_PATTERNS);
        List<String> materials = matchKeys(text, MATERIAL_PATTERNS);
        if (colors.isEmpty() && materials.isEmpty()) return input;
        return input.withColorAndMaterialPreferences(colors, materials);
    }

    private List<String> matchKeys(String text, Map<String, Pattern> patterns) {
        List<String> found = new ArrayList<>();
        patterns.forEach((key, pattern) -> {
            if (pattern.matcher(text).find()) found.add(key);
        });
        return found;
    }

    private static Map<String, Pattern> colorPatterns() {
        LinkedHashMap<String, Pattern> patterns = new LinkedHashMap<>();
        patterns.put("white", Pattern.compile("bijel|white"));
        patterns.put("black", Pattern.compile("crn|black"));
        patterns.put("grey", Pattern.compile("siv|grey|gray|antracit"));
        patterns.put("beige", Pattern.compile("krem|cream|bjelokost|ivory"));
        patterns.put("brown", Pattern.compile("smed|braon|brown"));
        patterns.put("green", Pattern.compile("zelen|green|maslinast"));
        patterns.put("blue", Pattern.compile("plav|blue|teget|navy"));
        patterns.put("yellow", Pattern.compile("zut|yellow|oker"));
        patterns.put("red", Pattern.compile("crven|bordo|\\bred\\b"));
        patterns.put("pink", Pattern.compile("roza|roze|pink"));
        patterns.put("natural", Pattern.compile("prirodn|natural|hrast|oak"));
        patterns.put("gold", Pattern.compile("zlatn|gold|mjed"));
        return patterns;
    }

    private static Map<String, Pattern> materialPatterns() {
        LinkedHashMap<String, Pattern> patterns = new LinkedHashMap<>();
        patterns.put("wood", Pattern.compile("drv|hrast|oak|orah|wood|bambus"));
        patterns.put("metal", Pattern.compile("metal|celik|aluminij|krom"));
        patterns.put("glass", Pattern.compile("stakl|glass"));
        patterns.put("fabric", Pattern.compile("tkanin|tekstil|pamuk|platno|fabric|\\blan\\b"));
        patterns.put("leather", Pattern.compile("koza|kozn|leather"));
        patterns.put("rattan", Pattern.compile("ratan|rattan|pleten"));
        patterns.put("marble", Pattern.compile("mramor|marble"));
        patterns.put("velvet", Pattern.compile("barsun|samt|velvet|plis"));
        return patterns;
    }

    private Optional<Integer> findBudget(String text) {
        List<Pattern> patterns = List.of(
                Pattern.compile("(\\d{3,5})\\s*(?:€|eur|eura|euro)"),
                Pattern.compile("(?:budget|budzet|do|ispod|maksimalno|maks|najvise|ne preko|ne vise od|imam|oko|otprilike)\\s*(\\d{3,5})")
        );
        int bestIndex = Integer.MAX_VALUE;
        Integer bestValue = null;
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find() && matcher.start() < bestIndex) {
                bestIndex = matcher.start();
                bestValue = Integer.parseInt(matcher.group(1));
            }
        }
        return Optional.ofNullable(bestValue);
    }

    private Optional<Integer> firstNumber(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return Optional.of(Integer.parseInt(matcher.group(1)));
        }
        return Optional.empty();
    }

    private boolean matches(String text, String regex) {
        return Pattern.compile(regex).matcher(text).find();
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private PlannerInputDto defaults() {
        return new PlannerInputDto("", 1500, "living-room", "bright", "Zagreb", 20, "multi",
                List.of("IKEA", "JYSK", "Pevex", "Emmezeta", "Decathlon", "Lesnina"),
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0);
    }

    private static Map<String, String> retailerStems() {
        LinkedHashMap<String, String> stems = new LinkedHashMap<>();
        stems.put("IKEA", "ikea");
        stems.put("JYSK", "jysk");
        stems.put("Pevex", "pevex");
        stems.put("Emmezeta", "emmezet");
        stems.put("Decathlon", "decathlon");
        stems.put("Lesnina", "lesnin");
        return stems;
    }
}
