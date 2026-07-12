package ai.budgetspace.planner;

import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.product.Markets;

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
            Map.entry("bed", Pattern.compile("krevet|\\bbed\\b|postelj|\\bbett|\\bletto\\b|\\blit\\b|\\bcama\\b|\\bseng|\\bsang\\b|\\bpostel|\\bkonty|\\blozko")),
            Map.entry("mattress", Pattern.compile("madrac|mattress|matratze|materass|matelas|colchon|colchao|patja|madrass|madras|matrac")),
            Map.entry("gym-equipment", Pattern.compile("bucic|utez|girja|klup|bench|sprava|traka za trcanje|bicikl|oprema za vjezb")),
            // Sprint 10.7: new-room categories. These are precise multi-word phrases so they do not
            // collide with the single-word kitchen/bedroom synonyms above.
            Map.entry("dining-table", Pattern.compile("blagovaonski stol|trpezarijski stol|stol za blagovanje|dining table")),
            Map.entry("dining-chair", Pattern.compile("blagovaonsk[ae] stolic|trpezarijsk[ae] stolic|dining chair")),
            Map.entry("kitchen-storage", Pattern.compile("kuhinjski ormar|kuhinjska polic|kuhinjsko spreman|kitchen storage")),
            Map.entry("kitchen-cart", Pattern.compile("kuhinjska kolica|servirna kolica|kitchen cart")),
            Map.entry("nightstand", Pattern.compile("nocn\\w* ormar|nightstand|nachttisch|comodino|table de chevet|nattbord|natbord")),
            Map.entry("wardrobe", Pattern.compile("ormar za odjec|garderobni ormar|plakar|wardrobe")),
            Map.entry("dresser", Pattern.compile("komoda s ladic|ladicar|dresser")),
            // Sprint 10.176 (kitchen Increment 3): kitchen appliances (parsed as must-have when named). A
            // "mikrovalna pecnica" is a microwave, so the oven pattern excludes that phrase.
            Map.entry("oven", Pattern.compile("(?<!mikrovaln. )pecnic|\\brerna\\b|backofen|\\boven\\b")),
            Map.entry("hob", Pattern.compile("ploca za kuhanj|indukcijsk\\w* ploc|\\bkuhalo\\b|kochfeld|\\bhob\\b|cooktop")),
            Map.entry("cooker-hood", Pattern.compile("\\bnapa\\b|\\bnapu\\b|kuhinjsk\\w* nap|dunstabzug|cooker hood|extractor hood")),
            Map.entry("fridge", Pattern.compile("hladnjak|hladilnik|frizid|frizd|kuhlschrank|\\bfridge\\b|refrigerat|frigo|jaakaappi|koelkast|chladnick|kylskap|kjoleskap|koleskab|geladeira|nevera")),
            Map.entry("freezer", Pattern.compile("zamrziva|\\bskrinj|gefrierschrank|\\bfreezer\\b")),
            Map.entry("dishwasher", Pattern.compile("peril\\w* posu|peril\\w* sud|geschirrspul|dishwasher|lave-vaisselle|lavavajillas|lavastoviglie")),
            Map.entry("microwave", Pattern.compile("mikrovaln|mikrowell|microwave")),
            // Sprint 10.181: bathroom fixtures. toilet + washbasin are their own categories; a bathtub vs a shower is
            // told apart (kada -> bathtub, tuš -> shower) so an explicit "želim kadu" / "treba mi tuš" is honored. The
            // words are multilingual and word-boundary-anchored so "status"/"blokade" never trip them.
            Map.entry("toilet", Pattern.compile("wc skoljk|\\bwc\\b|skoljk|zahod|monoblok|toalet|klozet|\\btoilet|stranisc|gabinetto|\\bwater\\b|sanitario|inodoro|sanita|vessa|toalett|\\bklo\\b|zachod")),
            Map.entry("washbasin", Pattern.compile("umivaonik|umivalnik|lavabo|\\bsink\\b|washbasin|waschbecken|\\bbasin\\b|lavandino|lavamanos|lavatorio|pesuallas|handvask|\\bvask\\b|tvattstall|umyvadlo|wastafel|handfat|vaskeservant|servant")),
            Map.entry("bathtub", Pattern.compile("\\bkad[aeiou]|bathtub|badewanne|\\bvasca\\b|baignoire|kylpyamme|badekar|ligbad")),
            Map.entry("shower", Pattern.compile("\\btus(?:a|u|em)?\\b|tus kabin|tus kad|tuskabin|\\bshower\\b|dusche|doccia|douche|ducha|duche|suihku|\\bdusj|\\bdusch|sprch|chuveiro|bruse"))
    );

    // Sprint 10.7: colour and material preferences. Keys are the canonical tags shared with
    // ProductTaxonomy.deriveColorTags / deriveMaterialTags, so a parsed preference can be matched
    // directly against a product's colorTags / materialTags. Patterns run over accent-free text.
    private static final Map<String, Pattern> COLOR_PATTERNS = colorPatterns();
    private static final Map<String, Pattern> MATERIAL_PATTERNS = materialPatterns();

    // Group 1 marks an "already have / exclude" clause, group 2 marks a "need" clause.
    // Longer and negated phrases come first so "ne treba mi" wins over "treba".
    private static final Pattern CLAUSE_TRIGGER = Pattern.compile(
            "(vec imam|imam vec|imam doma|imam kod kuce|ne treba mi|ne trebam|ne treba|ne dodavaj|ne dodaj|ne zelim"
                    + "|bez|brez|keine|\\bkein\\b|sans|senza|\\bsin\\b|\\bsem\\b|utan|\\buden\\b|\\buten\\b|ilman|zonder|\\bgeen\\b"
                    + "|preskoci|maknuti|makni|imam(?!\\s*\\d))"
                    + "|(treba mi|trebam|treba|fali mi|fali|dodaj|zelim|obavezno|prioritet|najvazniji|najvazni|najvise mi|volio bih|voljela bih)");

    // Sprint 10.181: object-verb (Croatian) reverse triggers — the owned/excluded noun comes BEFORE the verb.
    private static final Pattern REVERSE_HAVE = Pattern.compile("\\b(?:vec\\s+)?imam\\b(?!\\s*\\d)");
    private static final Pattern REVERSE_EXCLUDE = Pattern.compile("\\bnecu\\b|\\bne bih\\b|\\bne treba(?:m|ju)?\\b|\\bne zelim\\b");

    public PlannerInputDto enrich(PlannerInputDto rawInput) {
        PlannerInputDto input = rawInput == null ? defaults() : rawInput.normalized();
        String text = normalize(input.prompt());
        if (text.isBlank()) {
            return input;
        }

        Optional<Integer> budget = findBudget(text);
        if (budget.isPresent()) {
            // Sprint 10.74: currency-aware ceiling so a high-denomination budget (NOK/SEK/DKK) isn't capped at the
            // EUR ceiling (9000). The market is the user's; the catalog prices are in that currency.
            int ceiling = Markets.budgetCeiling(Markets.currencyFor(input.market()));
            input = input.withBudget(clamp(budget.get(), Math.max(1, ceiling / 90), ceiling));
        }

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
        // Sprint 10.169: an EXPLICIT non-default room selection from the UI wins over a room merely INFERRED from
        // the prompt text. The pre-filled example prompt is a living-room, so a user who picked Bathroom/Bedroom/etc.
        // in "detailed settings" but left the example text was silently overridden back to living-room. On the
        // living-room DEFAULT the prompt may still change the room (natural-language "make it a bedroom").
        //
        // Bug 2026-07-10: only honor the structured room when it was DELIBERATELY chosen (roomInferred=false). After
        // a generate, the frontend writes the INFERRED room back into the form (roomType="bathroom"), so the next
        // request carries a non-default room that is NOT a fresh UI pick. Without the roomInferred check, typing
        // "Kupaonica…" then "Spavaća soba…" returned the bathroom plan again (this early return swallowed the new
        // prompt). When roomInferred=true we fall through and re-derive from the prompt (and if the prompt names no
        // room, the last-match-wins parsing leaves the carried room untouched anyway).
        String selected = input.roomType();
        if (selected != null && !selected.isBlank() && !"living-room".equals(selected) && !input.roomInferred()) {
            return input;
        }
        // Sprint 10.135: the room keywords are MULTILINGUAL so the rule-based fallback (used when the LLM call
        // fails / is throttled / the daily AI cap is spent) still classifies the room in every market's language.
        // Before this, a non-HR/EN prompt silently fell back to living-room (no bed, ignored budget) on any LLM
        // hiccup. "Last match wins", so a later-checked room overrides; the studio container is checked last.
        if (matches(text, "dnevn|boravak|living|wohnzimmer|wohnraum|soggiorno|salotto|salon|sejour|woonkamer|zitkamer|obyvack|obyvacia|sala de estar|olohuone|vardagsrum|\\bstue")) input = input.withRoomType("living-room");
        if (matches(text, "radni kutak|radni prostor|home office|homeoffice|\\bured\\b|\\boffice\\b|posao|arbeitszimmer|\\bburo\\b|ufficio|bureau|werkkamer|kantoor|pracovn|oficina|despacho|escritorio|tyohuone|kotitoimisto|kontor|pisarn|delovni|radn\\w* sob|radnu sob")) input = input.withRoomType("home-office");
        if (matches(text, "spava|bedroom|spavac|schlafzimmer|camera da letto|chambre|slaapkamer|spaln|dormitorio|habitacion|recamara|quarto|makuuhuone|soverom|sovrum|sovevaer|makuu")) input = input.withRoomType("bedroom");
        // Sprint 10.79: home-gym de-scoped (no verified gym products) — a gym prompt no longer maps to it; the
        // room defaults instead so the user still gets a (non-empty) plan rather than an empty home-gym.
        // Sprint 10.7: new rooms. Checked after the originals, so the last room mentioned wins.
        if (matches(text, "kuhinj|kitchen|kuche|cucina|cuisine|keuken|kuchyn|cocina|cozinha|keitti|kjokken|kokken|\\bkok\\b|koket|kuhinu")) input = input.withRoomType("kitchen");
        // Sprint 10.176: a named kitchen APPLIANCE implies the kitchen room even without the word "kuhinja"
        // (e.g. "trebam pećnicu i frižider"), so the appliance is planned in the kitchen (its products' room).
        if (matches(text, "(?<!mikrovaln. )pecnic|hladnjak|frizider|perilic\\w* posu|\\bnapa\\b|zamrziva|mikrovaln|\\bkuhalo\\b|indukcijsk\\w* ploc")) input = input.withRoomType("kitchen");
        if (matches(text, "blagovaon|trpezarij|dining|esszimmer|sala da pranzo|salle a manger|eetkamer|jedalen|jedilnic|comedor|sala de jantar|ruokailu|spisestue|matsal|spisestue|spisrum")) input = input.withRoomType("dining-room");
        // Sprint 10.181: a dining-table / dining-chair phrase implies the dining room even without the room word.
        if (matches(text, "blagovaonski stol|trpezarijski stol|stol za blagovanje|za blagovanje|za objedovanje|blagovaonsk\\w* stolic|dining table|dining chair")) input = input.withRoomType("dining-room");
        if (matches(text, "hodnik|predsob|hallway|\\bflur\\b|diele|ingresso|corridoio|couloir|chodba|predsien|recibidor|pasillo|eteinen|korridor|\\bhall\\b|vorzimmer|entree|\\bentre\\b|vindfang|hodch|\\bhal\\b|\\bgang\\b|halle|hodnika")) input = input.withRoomType("hallway");
        // German "Bad" = bathroom, but a bare \bbad\b also matched the English adjective "bad" (e.g. "my sofa is
        // bad, help with the living room" was reclassified to bathroom). Require a German determiner/verb context
        // so the noun still resolves without hijacking English prompts; "badezimmer" still catches the full word.
        if (matches(text, "kupaon|kupatil|bathroom|badezimmer|(?:\\b(?:das|mein|meine|unser|unsere|euer|im|ins|ein)\\s+bad\\b|\\bbad\\s+(?:einricht|renovier|umbau|gestalt))|bagno|salle de bain|badkamer|kupeln|kopalnic|\\bbano\\b|cuarto de bano|casa de banho|banheiro|kylpyhuone|badevaer|badrum|baderom|baderum|badevaerelse|kopalnico|\\bbadet\\b|badezimer")) input = input.withRoomType("bathroom");
        // Sprint 10.181: a named bathroom FIXTURE implies the bathroom even without the word "kupaonica" (e.g.
        // "treba mi tuš i umivaonik") — mirrors the appliance→kitchen rule above. Word-boundary-anchored so
        // "status"/"blokade"/"komad" never mis-route.
        if (matches(text, "wc skoljk|\\bwc\\b|skoljk|umivaonik|umivalnik|lavabo|washbasin|\\btus(?:a|u|em)?\\b|tus kabin|\\bkad[aeiou]|bathtub|\\bshower\\b|\\btoilet\\b|stranisc|sprch")) input = input.withRoomType("bathroom");
        // Sprint 10.179: utility rooms (garage / pantry / laundry / attic / basement) — furnished from the shared
        // storage/lighting pool. Checked after the standard rooms; studio (combined room) still wins last. Patterns
        // are in the NORMALIZED form the text is matched in (ASCII, diacritics stripped: ž→z, š→s, č→c) and stay
        // precise — \bostava\b / \bostavu\b, so "dostava" (delivery) and "ostaviti" (to leave) never mis-route.
        if (matches(text, "garaz|radionic|\\bgarage\\b|werkstatt")) input = input.withRoomType("garage");
        if (matches(text, "spajz|smocnic|pantry|\\bostava\\b|\\bostavu\\b|\\bostavom\\b")) input = input.withRoomType("pantry");
        if (matches(text, "veseraj|praonic|perionic|laundry|waschkuche|waschraum")) input = input.withRoomType("laundry");
        if (matches(text, "tavan|potkrovlj|\\battic\\b|dachboden")) input = input.withRoomType("attic");
        if (matches(text, "podrum|basement|\\bcellar\\b|\\bkeller\\b|suteren")) input = input.withRoomType("basement");
        // Studio / one-room apartment is the COMBINED-room container (bed + seating + dining in one space); checked
        // last so it wins over any single-room word it co-occurs with. Bare "studio" leans studio-flat here (the
        // common furnishing sense); a rare IT/ES "studio/estudio"=home-office is left to the (primary) AI path.
        if (matches(text, "garsonijer|garsonjer|garson|garzon|\\bstudio\\b|bedsit|one-room|one room|jednosob|einzimmer|monolocale|monolokal|studette|eenkamer|monoambiente|kitnet|yksio|ettrom|ettrums|\\betta\\b")) input = input.withRoomType("studio");
        return input;
    }

    private PlannerInputDto applyStyle(String text, PlannerInputDto input) {
        if (matches(text, "ne znam|svejedno|predlozi")) input = input.withStyle("surprise");
        if (matches(text, "svijetl|prozrac|skandi|scandi|nordic|skandinav")) input = input.withStyle("bright");
        if (matches(text, "toplo|ugodno|mekano|domac|cozy")) input = input.withStyle("warm");
        if (matches(text, "moder|uredno")) input = input.withStyle("modern");
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
        // Seed from what the caller already chose (form / API), then let the prompt refine it.
        // Without this, an explicit excludedRetailers/preferredRetailers on the request would be
        // discarded whenever the prompt also mentions the retailers.
        LinkedHashSet<String> preferred = new LinkedHashSet<>(
                input.preferredRetailers() == null ? List.of() : input.preferredRetailers());
        LinkedHashSet<String> excluded = new LinkedHashSet<>(
                input.excludedRetailers() == null ? List.of() : input.excludedRetailers());
        List<String> mentioned = new ArrayList<>();

        for (Map.Entry<String, String> entry : RETAILER_STEMS.entrySet()) {
            String retailer = entry.getKey();
            String stem = entry.getValue();
            int idx = text.indexOf(stem);
            if (idx < 0) continue;
            mentioned.add(retailer);
            // Look back a short window, but stop at the previous clause boundary (comma/“;”) so a
            // word like "bez" in an earlier clause ("bez Lesnine, najviše IKEA") doesn't leak onto
            // the next retailer and flip "preferred" into "excluded".
            String before = text.substring(Math.max(0, idx - 22), idx);
            int boundary = Math.max(before.lastIndexOf(','), before.lastIndexOf(';'));
            if (boundary >= 0) before = before.substring(boundary + 1);
            // Sprint 10.181: also read a short window AFTER the retailer, bounded at the next clause, for the Croatian
            // object-verb negation "lesninu nemoj" / "ikeu ne treba" (the "no" follows the store name).
            String after = text.substring(Math.min(text.length(), idx + stem.length()),
                    Math.min(text.length(), idx + stem.length() + 16));
            int cComma = after.indexOf(',');
            int cSemi = after.indexOf(';');
            int cut = cComma < 0 ? cSemi : (cSemi < 0 ? cComma : Math.min(cComma, cSemi));
            if (cut >= 0) after = after.substring(0, cut);
            boolean excludeBefore = matches(before, "\\bbez\\b|izbjegni|izbaci|ne zelim|ne trebam|ne treba|\\bosim\\b|preskoci"
                    + "|brez|keine|\\bkein\\b|sans|senza|\\bsin\\b|utan|\\buden\\b|\\buten\\b|ilman|zonder|\\bgeen\\b|\\bno\\b|\\bnie\\b|niente");
            boolean excludeAfter = matches(after, "\\bnemoj\\b|ne treba|ne zelim|izbaci|izbjegni|preskoci");
            boolean preferBefore = matches(before, "najvise|radije|preferiram|ako moze|po mogucnosti|volio bih|voljela bih|prvenstveno|preferira"
                    + "|najraje|liebsten|de preference|preferibilmente|preferiblemente|de preferencia|mieluiten|helst|liefst|najradsej|\\bprefer");
            // "ikea može" / "ikea moze proci" — a mild preference where the OK follows the store name.
            boolean preferAfter = matches(after, "\\bmoze\\b|moze proci|u redu|smije");
            if (excludeBefore || excludeAfter) {
                excluded.add(retailer);
            } else if (preferBefore || preferAfter) {
                preferred.add(retailer);
            }
        }

        // Store limit: explicit numbers win over the soft "fewer stores" wish.
        int maxStores = 0;
        if (matches(text, "jedna trgovina|jednu trgovinu|samo jedna|iz jedne trgovine|jedan odlazak|sve iz jedne"
                + "|ene trgovine|eni trgovini|einem geschaft|einem laden|einem geschaeft|un negozio|un solo negozio"
                + "|yhdesta|un seul magasin|une seule|un magasin|een winkel|jednej predajne|jednom obchode|una tienda"
                + "|numa loja|uma loja|en butikk|en butik|ett stalle|one store|one shop|single store|samo iz ene")) {
            maxStores = 1;
        } else if (matches(text, "dvije trgovine|dvije trgovina|maksimalno dvije|maks dvije|ne vise od dvije|do dvije trgovine|najvise dvije|dvije trgovin")) {
            maxStores = 2;
        } else if (matches(text, "ne zelim puno trgovina|sto manje trgovina|manje trgovina|bez puno obilazaka|bez obilazaka|manje obilazaka|ne zelim obilaziti|bez puno trgovina|ne bi obilaz|ne bih obilaz|ne bi da obilaz")) {
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

        // Sprint 10.181: telegraphic prompts with NO clause trigger at all ("kuhinja 2k frizider napa",
        // "ured 600 stol i stolica") — the user is just listing what they want, so treat every named category as a
        // wish. Skipped whenever any need/have/exclude trigger is present (then the clause logic above is authoritative).
        if (markers.isEmpty()) {
            mustHave.addAll(categoriesIn(text));
        }

        // Sprint 10.181: object-verb word order (Croatian) — the noun PRECEDES the verb: "krevet imam" (I have a
        // bed), "kadu necu" (I don't want a bathtub). The forward clause scan above misses these because it only
        // reads what comes AFTER a trigger, so scan the short window BEFORE each such verb.
        reverseClauseCategories(text, REVERSE_HAVE, alreadyHave, false);
        LinkedHashSet<String> excluded = new LinkedHashSet<>();
        reverseClauseCategories(text, REVERSE_EXCLUDE, excluded, true);
        mustHave.removeAll(excluded);        // "kadu necu" wins over a forward mis-capture of "kadu" as a wish
        alreadyHave.addAll(excluded);        // an excluded fixture is treated as not-to-add (drives excludedFixtureFacet)

        // Sprint 10.181: bathroom fixtures are unambiguous wants — a mentioned toilet/washbasin/bathtub/shower is a
        // wish unless it's the one being excluded ("bez kade"). Catches triggerless "tuš, bez kade" (want the shower,
        // drop the tub) and "shower bez bathtub" where the wanted fixture carries no explicit "treba mi".
        for (String fixture : List.of("toilet", "washbasin", "bathtub", "shower")) {
            if (!excluded.contains(fixture) && !alreadyHave.contains(fixture)
                    && CATEGORY_PATTERNS.get(fixture).matcher(text).find()) {
                mustHave.add(fixture);
            }
        }

        // A category the user explicitly asked for wins over an ambiguous "already have".
        // e.g. "već imam TV ... treba mi TV komoda" -> tv-unit stays requested.
        alreadyHave.removeAll(mustHave);

        return input.withCategories(new ArrayList<>(mustHave), new ArrayList<>(alreadyHave));
    }

    // Object-verb order: find each have/exclude verb and read categories from the short window BEFORE it, bounded at
    // the previous clause boundary (comma/;) so a preceding clause's noun never leaks onto this verb. For an EXCLUDE
    // ("kadu necu") the negation targets the NEAREST noun only ("tuš, kadu neću" excludes the tub, keeps the shower);
    // for a HAVE ("krevet i ormar imam") all nouns in the window are owned.
    private void reverseClauseCategories(String text, Pattern verb, LinkedHashSet<String> target, boolean nearestOnly) {
        Matcher matcher = verb.matcher(text);
        while (matcher.find()) {
            String before = text.substring(Math.max(0, matcher.start() - 28), matcher.start());
            int boundary = Math.max(before.lastIndexOf(','), before.lastIndexOf(';'));
            if (boundary >= 0) before = before.substring(boundary + 1);
            if (!nearestOnly) {
                target.addAll(categoriesIn(before));
                continue;
            }
            String nearest = null;
            int bestEnd = -1;
            for (Map.Entry<String, Pattern> entry : CATEGORY_PATTERNS.entrySet()) {
                Matcher cm = entry.getValue().matcher(before);
                int lastEnd = -1;
                while (cm.find()) lastEnd = cm.end();
                if (lastEnd > bestEnd) { bestEnd = lastEnd; nearest = entry.getKey(); }
            }
            if (nearest != null) target.add(nearest);
        }
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
        // Sprint 10.181: budget/amount parsing lives in the centralized, unit-tested AmountParser so the rule-based
        // fallback reads how real users actually write money — grouped thousands ("1.500 €"), multi-currency
        // ("9000 kr", "£1800"), the "k" shorthand ("1.5k"), the "e" shorthand ("800e"), a bare standalone number
        // ("boravak 1000") and Croatian number-words/slang ("soma", "dva soma", "soma i po") — while never turning a
        // room size, quantity, phone/order number or year into a budget. See AmountParser + AmountParserTest.
        return AmountParser.parseBudget(text);
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
        // Sprint 10.181: NFD + strip combining marks handles ž/š/č/ć/ä/ö/é, but the Nordic/German ligatures æ ø å ß
        // are NOT decomposed by NFD, so a Danish "soveværelse"/"badeværelse" or German "Küche/Straße" never matched an
        // ASCII pattern. Fold them explicitly so the multilingual room/budget rules work in NO/SE/DK/DE.
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("æ", "ae").replace("ø", "o").replace("å", "a").replace("ß", "ss");
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
