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
            Map.entry("sofa", Pattern.compile("kauc|sofa|trosjed|dvosjed|garnitur|couch|divan[oi]|canape|sohva(?!p)")),
            Map.entry("tv-unit", Pattern.compile("tv komod|komoda za tv|tv element|tv unit|\\btv\\b|televizor")),
            Map.entry("table", Pattern.compile("\\bstolic\\b|klub stol|coffee table")),
            Map.entry("rug", Pattern.compile("tepih|\\bsag\\b|teppich|carpet|\\brug\\b|alfombra|\\btapis\\b|tappet")),
            Map.entry("lighting", Pattern.compile("lamp|rasvjet|svjetilj|svjetlo")),
            Map.entry("storage", Pattern.compile("polic|regal|ormar|spremanje|(?<!tv )komod")),
            Map.entry("decor", Pattern.compile("dekor|ukras|slik|jastuk|biljk|svijec")),
            Map.entry("desk", Pattern.compile("radni stol|pisaci stol|\\bdesk\\b|\\bschreibtisch\\b")),
            Map.entry("chair", Pattern.compile("\\bstolica\\b|\\bstolice\\b|\\bstolicu\\b|\\bstolicom\\b|fotelj|\\bchair\\b|stuhl|\\bsillas?\\b|\\bchaise|\\bsedi[ae]\\b")),
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
            Map.entry("wardrobe", Pattern.compile("ormar za odjec|garderobni ormar|plakar|wardrobe|armoire(?! de (?:toilette|cuisine))|\\barmadi[oi]\\b|\\barmarios?\\b")),
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

    // Sprint 10.190: "not too X" across the 15 markets. A degree phrase, not a plain negation — see applyStyle.
    private static final Pattern SOFTENER = Pattern.compile(
            "\\b(?:ne previse|nije pretjerano|ne bas|not too|nothing too|not overly|nicht zu|non troppo"
            + "|no demasiado|nao muito|pas trop|niet te|ei liian|inte for|ikke for)\\b");

    // The style a softened request should be blended TOWARD. "ne previše moderno" -> modern + classic.
    private static final Map<String, String> STYLE_COMPLEMENT = Map.of(
            "modern", "classic",
            "classic", "modern",
            "minimal", "warm",
            "warm", "bright",
            "bright", "warm",
            "industrial", "warm",
            "boho", "minimal");

    // Sprint 10.190: every way a user asks for a lower price, in one place — used both by the goal rules and by
    // the negated-price inversion below them.
    private static final String PRICE_DOWN =
            "najj?eftin|sto jeftin|low cost|jeftin|povoljn|poceni|budget|gunstig|guenstig|billig|cheap|barat|econom"
            + "|pas cher|moins cher|bon marche";

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
        // Sprint 10.190: which parts of the sentence the user NEGATED. Computed once and consulted by every
        // preference rule below, so "ne želim tamno" never sets the industrial style.
        NegationScope scope = NegationScope.of(text);

        Optional<Integer> budget = findBudget(text);
        if (budget.isPresent()) {
            // Sprint 10.74: currency-aware ceiling so a high-denomination budget (NOK/SEK/DKK) isn't capped at the
            // EUR ceiling (9000). The market is the user's; the catalog prices are in that currency.
            int ceiling = Markets.budgetCeiling(Markets.currencyFor(input.market()));
            input = input.withBudget(clamp(budget.get(), Math.max(1, ceiling / 90), ceiling));
        }

        Optional<Integer> size = firstNumber(text, Pattern.compile("(\\d{1,2})\\s*(m2|m²|kvadrat)"));
        if (size.isPresent()) input = input.withSize(clamp(size.get(), 8, 60));

        input = applyRoom(text, input, scope);
        input = applyStyle(text, input, scope);
        input = applyFurnishingLevel(text, input, scope);
        input = applyOptimizationGoal(text, input, scope);
        input = applyRetailerIntent(text, input, scope);
        input = applyCategories(text, input);
        input = applyColorAndMaterialPreferences(text, input, scope);

        return input;
    }

    private PlannerInputDto applyRoom(String text, PlannerInputDto input, NegationScope scope) {
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
        // Sprint 10.135: the room keywords are MULTILINGUAL so the rule-based fallback still classifies the room in
        // every market's language. "Last match wins", so a later-checked room overrides. `named` tracks whether an
        // EXPLICIT room NAME matched, so the item-implied inferences below don't override an explicitly stated room.
        boolean named = false;
        // Sprint 10.190: utility rooms are checked BEFORE the habitable ones (was: after). A utility word is often a
        // LOCATION ("spavaću sobu u potkrovlju"); a habitable room named alongside it should win under last-match-wins.
        // A pure "uredi potkrovlje" (no habitable room) still resolves to attic. Sprint 10.190 also adds the Nordic/NL
        // basement stems (kjeller/kælder/källare/kelder). Patterns stay in NORMALIZED form (ž→z, š→s, č→c).
        if (affirmative(text, "garaz|radionic|\\bgarage\\b|werkstatt|garaje|garasje|autotalli", scope)) { input = input.withRoomType("garage"); named = true; }
        if (affirmative(text, "spajz|smocnic|pantry|\\bostava\\b|\\bostavu\\b|\\bostavom\\b|\\bdespensa|\\bdispensa", scope)) { input = input.withRoomType("pantry"); named = true; }
        if (affirmative(text, "veseraj|praonic|perionic|laundry|waschkuche|waschraum|lavanderi|lavandari|vaskerom|vaskerum|tvattstug", scope)) { input = input.withRoomType("laundry"); named = true; }
        if (affirmative(text, "tavan|potkrovlj|\\battic\\b|dachboden|mansard|soffitt[ae]", scope)) { input = input.withRoomType("attic"); named = true; }
        if (affirmative(text, "podrum|basement|\\bcellar\\b|\\bkeller\\b|kjeller|kaelder|kallare|\\bkelder\\b|suteren|sotano|kellari", scope)) { input = input.withRoomType("basement"); named = true; }
        // Habitable room NAMES.
        if (affirmative(text, "dnevn|boravak|living|wohnzimmer|wohnraum|soggiorno|salotto|salon|sejour|woonkamer|zitkamer|obyvack|obyvacia|sala de estar|olohuone|vardagsrum|\\bstue", scope)) { input = input.withRoomType("living-room"); named = true; }
        // Sprint 10.190: SK "kancelar" (office), SI "delovn* sob", EN "WFH"/"work from home"/"study room".
        if (affirmative(text, "radni kutak|radni prostor|home office|homeoffice|\\bured\\b|\\boffice\\b|posao|arbeitszimmer|\\bburo\\b|ufficio|bureau|werkkamer|kantoor|pracovn|kancelar|oficina|despacho|escritorio|tyohuone|kotitoimisto|kontor|pisarn|delovn\\w* sob|\\bwfh\\b|work from home|working from home|study room|radn\\w* sob|radnu sob", scope)) { input = input.withRoomType("home-office"); named = true; }
        if (affirmative(text, "spava|bedroom|spavac|schlafzimmer|camera da letto|chambre|slaapkamer|spaln|dormitorio|habitacion|recamara|quarto|makuuhuone|soverom|sovrum|sovevaer|makuu", scope)) { input = input.withRoomType("bedroom"); named = true; }
        if (affirmative(text, "kuhinj|kitchen|kuche|cucina|cuisine|keuken|kuchyn|cocina|cozinha|keitti|kjokken|kokken|\\bkok\\b|koket|kuhinu", scope)) { input = input.withRoomType("kitchen"); named = true; }
        if (affirmative(text, "blagovaon|trpezarij|dining|esszimmer|sala da pranzo|salle a manger|eetkamer|jedalen|jedilnic|comedor|sala de jantar|ruokailu|spisestue|matsal|spisestue|spisrum", scope)) { input = input.withRoomType("dining-room"); named = true; }
        if (affirmative(text, "hodnik|predsob|hallway|\\bflur\\b|diele|ingresso|corridoio|couloir|chodba|predsien|recibidor|pasillo|eteinen|korridor|\\bhall\\b|vorzimmer|entree|\\bentre\\b|vindfang|hodch|\\bhal\\b|\\bgang\\b|halle|hodnika|\\bcorredor\\b", scope)) { input = input.withRoomType("hallway"); named = true; }
        // German "Bad" = bathroom, but a bare \bbad\b also matched the English adjective "bad", so require a German
        // determiner/verb context; "badezimmer" still catches the full word.
        if (affirmative(text, "kupaon|kupatil|bathroom|badezimmer|(?:\\b(?:das|mein|meine|unser|unsere|euer|im|ins|ein)\\s+bad\\b|\\bbad\\s+(?:einricht|renovier|umbau|gestalt))|bagno|salle de bain|badkamer|kupeln|kopalnic|\\bbano\\b|cuarto de bano|casa de banho|banheiro|kylpyhuone|badevaer|badrum|baderom|baderum|badevaerelse|kopalnico|\\bbadet\\b|badezimer", scope)) { input = input.withRoomType("bathroom"); named = true; }
        // Sprint 10.176/10.181/10.190: item-IMPLIED rooms — a named kitchen appliance / dining-table phrase / bathroom
        // fixture implies its room, but ONLY when no explicit room was named. So "u dnevnom boravku mali frižider"
        // stays living-room instead of being pulled to the kitchen by the fridge.
        if (!named) {
            if (affirmative(text, "(?<!mikrovaln. )pecnic|hladnjak|frizider|perilic\\w* posu|\\bnapa\\b|zamrziva|mikrovaln|\\bkuhalo\\b|indukcijsk\\w* ploc", scope)) input = input.withRoomType("kitchen");
            if (affirmative(text, "blagovaonski stol|trpezarijski stol|stol za blagovanje|za blagovanje|za objedovanje|blagovaonsk\\w* stolic|dining table|dining chair", scope)) input = input.withRoomType("dining-room");
            if (affirmative(text, "wc skoljk|\\bwc\\b|skoljk|umivaonik|umivalnik|lavabo|washbasin|\\btus(?:a|u|em)?\\b|tus kabin|\\bkad[aeiou]|bathtub|\\bshower\\b|\\btoilet\\b|stranisc|sprch", scope)) input = input.withRoomType("bathroom");
        }
        // Studio / one-room apartment is the COMBINED-room container (bed + seating + dining in one space); checked
        // last so it wins over any single-room word it co-occurs with.
        if (affirmative(text, "garsonijer|garsonjer|garson|garzon|\\bstudio\\b|bedsit|one-room|one room|jednosob|einzimmer|monolocale|monolokal|studette|eenkamer|monoambiente|kitnet|yksio|ettrom|ettrums|\\betta\\b", scope)) input = input.withRoomType("studio");
        return input;
    }

    private PlannerInputDto applyStyle(String text, PlannerInputDto input, NegationScope scope) {
        if (affirmative(text, "ne znam|svejedno|predlozi", scope)) input = input.withStyle("surprise");
        if (affirmative(text, "svijetl|prozrac|skandi|scandi|nordic|skandinav", scope)) input = input.withStyle("bright");
        if (affirmative(text, "toplo|ugodno|mekano|domac|cozy|cosy|\\bwarm\\b|\\bgemue?tlich|acoged|accoglient|\\bcaldo\\b|hyggel|kodik|chaleureu", scope)) input = input.withStyle("warm");
        if (affirmative(text, "moder|uredno", scope)) input = input.withStyle("modern");
        if (affirmative(text, "minimal|jednostavn|cisto", scope)) input = input.withStyle("minimal");
        if (affirmative(text, "classic|klasic|klasc|klassi", scope)) input = input.withStyle("classic");
        if (affirmative(text, "industrial|industrij|tamno|crno|metal", scope)) input = input.withStyle("industrial");
        if (affirmative(text, "boho|prirodn|biljk|ratan|natural", scope)) input = input.withStyle("boho");
        // Sprint 10.190: "ne previše moderno" is NOT a plain negation — the user wants a SOFTER modern, not none.
        // Keep the named style as the primary and add its complement, so the scorer can favour a piece that reads
        // as both ("blagi modern ali classy"). Runs last and returns directly, so it wins over the suppression the
        // negation scope applied to the same word above.
        Matcher softener = SOFTENER.matcher(text);
        while (softener.find()) {
            // only the short window right after the degree phrase, so the rest of the sentence isn't swallowed
            String window = text.substring(softener.end(), Math.min(text.length(), softener.end() + 24));
            String softened = styleIn(window);
            if (softened != null) {
                return input.withStyle(softened)
                        .withSecondaryStyles(List.of(STYLE_COMPLEMENT.getOrDefault(softened, "warm")));
            }
        }
        return input;
    }

    /** The style named inside a softener's window, or null when the phrase softens something else ("not too basic"). */
    private String styleIn(String window) {
        if (matches(window, "moder|uredno")) return "modern";
        if (matches(window, "minimal|jednostavn|cisto")) return "minimal";
        if (matches(window, "classic|klasic|klasc|klassi")) return "classic";
        if (matches(window, "industrial|industrij|tamno|crno|metal")) return "industrial";
        if (matches(window, "boho|prirodn|biljk|ratan|natural")) return "boho";
        if (matches(window, "svijetl|prozrac|skandi|scandi|nordic|skandinav")) return "bright";
        if (matches(window, "toplo|ugodno|mekano|domac|cozy|cosy|\\bwarm\\b|\\bgemue?tlich|acoged|accoglient|\\bcaldo\\b|hyggel|kodik|chaleureu")) return "warm";
        return null;
    }

    private PlannerInputDto applyFurnishingLevel(String text, PlannerInputDto input, NegationScope scope) {
        if (affirmative(text, "osnovno|samo osnov|minimalno oprem|samo najvaznij|najnuzn|\\bbasics?\\b|bare essential|bare necessit|just the essential|essentials only", scope)) input = input.withFurnishingLevel("basic");
        if (affirmative(text, "udobnij|normalno|dovoljno komplet", scope)) input = input.withFurnishingLevel("comfort");
        if (affirmative(text, "kompletno|sve oprem|opremi sve|\\bfull\\b|dovrsen|odmah gotovo|komplett|fully furnished|\\bcomplet[oae]?\\b|od poda do stropa", scope)) input = input.withFurnishingLevel("complete");
        return input;
    }

    private PlannerInputDto applyOptimizationGoal(String text, PlannerInputDto input, NegationScope scope) {
        // Sprint 10.190: "jeftinije/cheaper" asks for a lower price BAND, not the floor. Only the SUPERLATIVE
        // ("najjeftinije / što jeftinije / cheapest / so günstig wie möglich") goes all the way down. Both are
        // checked, superlative last so it wins when a prompt carries both.
        if (affirmative(text, "jeftin|povoljn|poceni|cheap|gunstig|guenstig|billig|barat|econom|pas cher|moins cher"
                + "|bon marche|low cost|budget", scope)) {
            input = input.withOptimizationGoal("lower-price");
        }
        if (affirmative(text, "najj?eftin|sto jeftin|sto povoljnije|cheapest|am gunstigsten|so gunstig wie moglich"
                + "|so billig wie moglich|am billigsten|\\bbilligst|lo mas barato|il piu economico|le moins cher"
                + "|mais barato possivel", scope)) {
            input = input.withOptimizationGoal("lowest-price");
        }
        if (affirmative(text, "best value|omjer|balans|vrijednost", scope)) input = input.withOptimizationGoal("best-value");
        // Sprint 10.183: recognise the EVERYDAY way people ask for a good-looking room — "da bude lijepo", "lijepa
        // soba", "neka bude ljepše", "dopadljivo" — not just the formal "najljepše/estetski/što ljepše/ljepša
        // verzija". Text is accent-stripped (ž/š/č→z/s/c), so "ljepše"→"ljepse", "lijepu"→"lijepu". These map to
        // style-match, which flips the plan to spend-up (prefersQuality) and prefers better-rated, style-coherent
        // pieces instead of flooring to the cheapest — what a "dnevni boravak 2000, da bude lijepo" prompt wants.
        if (affirmative(text, "najljep|estetsk|ljepsa verzij|sto ljepse|lijep|ljeps|dopadljiv|bonit|\\bbell[oa]\\b|elegant|beautiful|gorgeous", scope)) input = input.withOptimizationGoal("style-match");
        // Sprint 10.190: the single deliberate INVERSION. "neću jeftino" / "keine billigen Möbel" / "nothing
        // cheap" is a quality signal, not merely an absent one, so it maps to the existing spend-up path. The
        // condition is "a price-down word is present but every occurrence of it is negated", which is why an
        // ordinary affirmative "jeftino" can never reach it. Checked last, so it wins over the rules above.
        if (!affirmative(text, PRICE_DOWN, scope) && matches(text, PRICE_DOWN)) {
            input = input.withOptimizationGoal("style-match");
        }
        return input;
    }

    private PlannerInputDto applyRetailerIntent(String text, PlannerInputDto input, NegationScope scope) {
        // Seed from what the caller already chose (form / API), then let the prompt refine it.
        // Without this, an explicit excludedRetailers/preferredRetailers on the request would be
        // discarded whenever the prompt also mentions the retailers.
        LinkedHashSet<String> preferred = new LinkedHashSet<>(
                input.preferredRetailers() == null ? List.of() : input.preferredRetailers());
        LinkedHashSet<String> excluded = new LinkedHashSet<>(
                input.excludedRetailers() == null ? List.of() : input.excludedRetailers());
        List<String> mentioned = new ArrayList<>();

        // Sprint 10.186: fold common IKEA misspellings / Croatian declensions to the canonical stem so "ikee"
        // (typo), "iz ikee" (genitive), "ikeu"/"ikeom" are still recognised as IKEA. (JYSK declensions "jyska",
        // "jysku", "jyskom" already contain the "jysk" stem, so the substring match catches them as-is.)
        text = text.replaceAll("\\bike(?:a|ee|e|u|i|om|ja|je|ju)\\b", "ikea");

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
                    // Sprint 10.186: Croatian "neću/nećemo" (won't) + "ništa iz/od" (nothing from) — the common way
                    // users decline a store ("neću ništa iz JYSKa") that the earlier list missed.
                    + "|necu|necemo|\\bnece\\b|nista iz|nista od|ne bih|ne bi\\b"
                    + "|brez|keine|\\bkein\\b|sans|senza|\\bsin\\b|utan|\\buden\\b|\\buten\\b|ilman|zonder|\\bgeen\\b|\\bno\\b|\\bnie\\b|niente"
                    // Sprint 10.189 audit: DE "ohne", PT "sem", Scandinavian "ingen" (no/none) were missing.
                    + "|\\bohne\\b|\\bsem\\b|\\bingen\\b"
                    // Sprint 10.190 adversarial: EN "without", Scandinavian "ikke/inte" (not), SK/CZ "nechc" (won't),
                    // FR "evit" (avoid), Romance "except/salvo/tranne/menos" (all but).
                    + "|\\bwithout\\b|\\bikke\\b|\\binte\\b|\\bnechc|\\bevit|excepto|exceto|\\bsalvo\\b|\\btranne\\b|todo menos|\\bmenos\\b");
            boolean excludeAfter = matches(after, "\\bnemoj\\b|ne treba|ne zelim|izbaci|izbjegni|preskoci");
            boolean preferBefore = matches(before, "najvise|radije|preferiram|ako moze|po mogucnosti|volio bih|voljela bih|prvenstveno|preferira"
                    + "|najraje|liebsten|de preference|preferibilmente|preferiblemente|de preferencia|mieluiten|helst|liefst|najradsej|\\bprefer"
                    // Sprint 10.189 audit: ES "prefiero", PT "prefiro", DE "bevorzuge", NL "liever".
                    + "|\\bprefier|\\bprefir|bevorzug|\\bliever\\b"
                    // Sprint 10.190 adversarial: EN "ideally", "stick to/with".
                    + "|\\bideally\\b|stick to|stick with");
            // "ikea može" / "ikea moze proci" — a mild preference where the OK follows the store name; 10.190:
            // German/Italian verb-final "JYSK bevorzugen" / "da JYSK preferisco".
            boolean preferAfter = matches(after, "\\bmoze\\b|moze proci|u redu|smije|bevorzug|\\bprefer|preferisc");
            // Sprint 10.190: "nicht ohne IKEA" / "ne bez IKEA" — the exclusion is ITSELF negated, so the two
            // cancel out and the store becomes a preference instead. isNegated() can't express this (it reads
            // false both for "no negation" and for "negated twice"), hence the dedicated isDoubleNegated.
            if ((excludeBefore || excludeAfter) && scope.isDoubleNegated(idx)) {
                preferred.add(retailer);
            } else if (excludeBefore || excludeAfter) {
                excluded.add(retailer);
            } else if (preferBefore || preferAfter) {
                preferred.add(retailer);
            }
        }

        // Store limit: explicit numbers win over the soft "fewer stores" wish.
        int maxStores = 0;
        // NOTE (10.190): the store-limit phrasings are deliberately NOT negation-scoped. "ne želim više od dvije
        // trgovine" / "ne želim puno trgovina" / "bez obilazaka" ARE the request — the negation is part of the
        // idiom, and the token that matches ("dvije trgovine") sits after the cue, so scoping it would suppress
        // the very rule it belongs to.
        if (matches(text, "jedna trgovina|jednu trgovinu|samo jedna|iz jedne trgovine|jedan odlazak|sve iz jedne"
                + "|ene trgovine|eni trgovini|einem geschaft|einem laden|einem geschaeft|un negozio|un solo negozio"
                + "|yhdesta|un seul magasin|une seule|un magasin|een winkel|jednej predajne|jednom obchode|una tienda"
                + "|numa loja|uma loja|en butikk|en butik|ett stalle|one store|one shop|single store|samo iz ene"
                // Sprint 10.189 audit: HR "jedan dućan" (colloquial for shop), SE "från en affär".
                + "|jedan ducan|jednog ducana|jednom ducanu|fran en affar")) {
            maxStores = 1;
        } else if (matches(text, "dvije trgovine|dvije trgovina|maksimalno dvije|maks dvije|ne vise od dvije|do dvije trgovine|najvise dvije|dvije trgovin|zwei geschaft|zwei geschaeft|zwei laden|zwei laeden|due negozi|dos tiendas")) {
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

        // Sprint 10.190 adversarial: when the ONLY named store is itself negated ("samo ne Pevex", "samo ne IKEA")
        // it must be EXCLUDED, never turned into the single store. isNegated is true only for a store that sits in a
        // negated clause, so a plain "samo IKEA" (no negation) is untouched.
        String soleStore = mentioned.size() == 1 ? mentioned.get(0) : null;
        boolean soleNegated = soleStore != null && scope.isNegated(text.indexOf(RETAILER_STEMS.get(soleStore)));
        if (soleNegated && !excluded.contains(soleStore) && !preferred.contains(soleStore)) {
            excluded.add(soleStore);
        }

        // Sprint 10.186: "only X" restricts to one store. Multilingual (the app serves 14 locales), not Croatian-only —
        // the live audit caught English "only JYSK" leaking IKEA because "only" was missing here. 10.190: IT "tutto da".
        boolean explicitSingle = mentioned.size() == 1 && !soleNegated && matches(text,
                "samo|iskljucivo|sve iz|tutto da|tutto dal|tutto dai|jedino|\\bonly\\b|exclusively|solely|\\bnur\\b|uniquement|seulement"
                + "|\\bsolo\\b|solamente|\\bapenas\\b|\\balleen\\b|edino|\\bvain\\b|ainoastaan|endast|\\bbara\\b|\\bbare\\b|\\bkun\\b");
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

    /**
     * Sprint 10.186: apply ONLY the retailer intent (exclude / only / prefer / store-count) parsed from a raw
     * prompt onto a seed input. The AI path needs this because the LLM schema exposes only a soft
     * {@code preferredRetailers} — so "ne želim IKEA" / "samo JYSK" would otherwise be lost. Runs the same
     * normalization + rules as the rule-based path, seeded with whatever retailer intent the caller already has.
     */
    public PlannerInputDto applyRetailerIntentFromPrompt(String rawPrompt, PlannerInputDto seed) {
        if (rawPrompt == null || rawPrompt.isBlank() || seed == null) return seed;
        String text = normalize(rawPrompt);
        return applyRetailerIntent(text, seed, NegationScope.of(text));
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
    private PlannerInputDto applyColorAndMaterialPreferences(String text, PlannerInputDto input, NegationScope scope) {
        List<String> colors = matchKeys(text, COLOR_PATTERNS, scope);
        List<String> materials = matchKeys(text, MATERIAL_PATTERNS, scope);
        if (colors.isEmpty() && materials.isEmpty()) return input;
        return input.withColorAndMaterialPreferences(colors, materials);
    }

    // Sprint 10.190: a colour/material named inside a negated clause ("bez crne boje") is not a preference.
    private List<String> matchKeys(String text, Map<String, Pattern> patterns, NegationScope scope) {
        List<String> found = new ArrayList<>();
        patterns.forEach((key, pattern) -> {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                if (!scope.isNegated(matcher.start())) { found.add(key); return; }
            }
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

    /**
     * Sprint 10.190: like {@link #matches} but a hit that sits inside a NEGATED clause does not count. Every
     * occurrence is scanned, so "ne zelim tamno, hocu tamni stol" still sees the affirmative second mention.
     * Offsets are absolute in {@code text}, which is why this must never be handed a substring.
     */
    private boolean affirmative(String text, String regex, NegationScope scope) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        while (matcher.find()) {
            if (!scope.isNegated(matcher.start())) return true;
        }
        return false;
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
