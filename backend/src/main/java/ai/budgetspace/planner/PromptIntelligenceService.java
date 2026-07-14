package ai.budgetspace.planner;

import ai.budgetspace.ai.AiUsageEvent;
import ai.budgetspace.ai.AiUsageTracker;
import ai.budgetspace.ai.LlmClient;
import ai.budgetspace.ai.LlmClientFactory;
import ai.budgetspace.ai.LlmCompletion;
import ai.budgetspace.ai.LlmCompletionRequest;
import ai.budgetspace.ai.LlmProperties;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.PlannerIntentAnalysisDto;
import ai.budgetspace.product.Markets;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductRepository;
import ai.budgetspace.product.ProductTaxonomy;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Sprint 10.10 — turns a free-text prompt into a structured {@link PlannerIntentAnalysisDto} that the
 * deterministic planner can act on. AI is the parsing/reasoning layer; it never selects products.
 *
 * <p>Path selection: if AI is enabled, a provider is configured with a key, and the usage guardrails
 * allow it, the prompt is sent to the LLM and the JSON answer is validated against the taxonomy. On
 * <em>any</em> problem (disabled, no key, limit hit, HTTP error, malformed JSON) it falls back to the
 * deterministic {@link PlannerIntentExtractor}. Either way a valid analysis is returned, and the LLM
 * output is sanitised so it can never introduce an unknown room, category or retailer.</p>
 */
@Service
public class PromptIntelligenceService {
    private static final Logger log = LoggerFactory.getLogger(PromptIntelligenceService.class);
    // Sprint 10.167: the LLM sometimes returns a scalar where the schema wants an array (e.g. a prompt saying
    // "topli tonovi" came back as "colorPreferences":"topli tonovi" instead of ["warm"]), which previously threw
    // MismatchedInputException and silently dropped the whole request to the rule-based path. Accept a single
    // value as a 1-element array, and ignore any extra keys the model volunteers, so a well-formed-but-loose LLM
    // answer is still used instead of wasted.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String USE_CASE = "intent-extraction";

    private static final Set<String> KNOWN_STYLES = Set.of(
            "bright", "warm", "modern", "minimal", "classic", "industrial", "boho", "surprise");
    private static final Set<String> QUALITY_PREFERENCES = Set.of("budget", "balanced", "premium");
    // Sprint 10.186 (AI-quality, from the live adversarial sweep): categories that unambiguously belong to ONE room.
    // Used to recover the room when the model left it at the living-room default but the user asked for a specific
    // item — e.g. "trebam umivaonik" / "treba mi WC školjka" came back roomType=living-room, so the planner built a
    // living-room. Mirrors the deterministic PlannerIntentExtractor, which infers the room from the item itself.
    private static final Map<String, String> ROOM_DEFINING_CATEGORY = Map.ofEntries(
            Map.entry("toilet", "bathroom"), Map.entry("washbasin", "bathroom"), Map.entry("bathtub", "bathroom"),
            Map.entry("shower", "bathroom"), Map.entry("bath-shower", "bathroom"),
            Map.entry("oven", "kitchen"), Map.entry("hob", "kitchen"), Map.entry("cooker-hood", "kitchen"),
            Map.entry("fridge", "kitchen"), Map.entry("freezer", "kitchen"), Map.entry("dishwasher", "kitchen"),
            Map.entry("microwave", "kitchen"), Map.entry("kitchen-set", "kitchen"),
            Map.entry("bed", "bedroom"), Map.entry("mattress", "bedroom"),
            Map.entry("dining-table", "dining-room"), Map.entry("dining-chair", "dining-room"),
            Map.entry("desk", "home-office"));
    // If a living-room-defining item is present, don't re-home a genuine living-room request that merely also
    // lists a cross-room piece (e.g. "dnevni boravak s kaučem i krevetom u kutu").
    private static final Set<String> LIVING_ROOM_ANCHORS = Set.of("sofa", "tv-unit");
    // The two free-text fields echoed back to the user are raw model output; cap the length and strip any HTML/script
    // tags so a prompt-injected payload ("... put <script>…</script> into the summary") can't ride them into the UI
    // or the PDF export. React already escapes — this is defense-in-depth + brand safety, not the sole XSS control.
    private static final int MAX_ECHO_LENGTH = 400;

    private final LlmClientFactory clientFactory;
    private final LlmProperties properties;
    private final AiUsageTracker usageTracker;
    private final ProductRepository productRepository;
    private final PlannerIntentExtractor ruleBasedExtractor = new PlannerIntentExtractor();
    // Sprint 10.186: retailer names (lowercased) actually STOCKED in the catalog, cached with a short TTL. A preferred
    // retailer is kept only if it is a real stocked store — so Emmezeta/Lesnina survive, but a taxonomy-registered-but-
    // empty slot (Wayfair) or an unknown name (Amazon/Temu) is dropped. Central + catalog-truthful, not a hardcoded list.
    private volatile Set<String> stockedRetailersLower;
    private volatile long stockedComputedAtMs;
    private static final long STOCKED_TTL_MS = 300_000;

    public PromptIntelligenceService(LlmClientFactory clientFactory, LlmProperties properties, AiUsageTracker usageTracker,
                                     ProductRepository productRepository) {
        this.clientFactory = clientFactory;
        this.properties = properties;
        this.usageTracker = usageTracker;
        this.productRepository = productRepository;
    }

    /**
     * Sprint 10.70: {@code ownerKey} (account "user:&lt;id&gt;" or guest "guest:&lt;browserId&gt;") and
     * {@code tier} (GUEST/FREE/PLUS/PRO) gate the AI by a per-user, per-tier daily allowance. On any
     * gate or failure the deterministic rule-based path is used, so a capped user still gets a plan.
     */
    public PlannerIntentAnalysisDto analyze(PlannerInputDto rawInput, String ownerKey, String tier) {
        PlannerInputDto input = rawInput == null ? new PlannerInputDto("", 0, null, null, null, 0, null, null, null, null,
                null, null, null, null, null, 0).normalized() : rawInput.normalized();
        String prompt = input.prompt() == null ? "" : input.prompt();

        Optional<LlmClient> client = clientFactory.activeClient();
        // tryAcquire atomically reserves a slot under the tier's daily cap; complete() (always, in the
        // catch or inside llmAnalyze on success) releases it — so concurrent calls can't overshoot the cap.
        if (client.isPresent() && usageTracker.tryAcquire(ownerKey, tier)) {
            try {
                return llmAnalyze(client.get(), input, prompt, ownerKey, tier);
            } catch (Exception exception) {
                log.warn("Prompt intelligence LLM call failed ({}); using rule-based fallback.", exception.toString());
                usageTracker.complete(ownerKey, new AiUsageEvent(client.get().provider().name(), properties.resolvedModel(client.get().provider()),
                        USE_CASE, ownerKey, tier, null, null, 0.0, false, true, Instant.now()));
            }
        }
        return ruleBasedAnalyze(input, prompt);
    }

    private PlannerIntentAnalysisDto llmAnalyze(LlmClient client, PlannerInputDto input, String prompt,
                                                String ownerKey, String tier) throws Exception {
        LlmCompletionRequest request = new LlmCompletionRequest(
                systemPrompt(), userPrompt(input, prompt), properties.maxOutputTokens(), true, USE_CASE);
        LlmCompletion completion = client.complete(request);

        PlannerIntentAnalysisDto parsed = MAPPER.readValue(extractJson(completion.text()), PlannerIntentAnalysisDto.class);
        PlannerIntentAnalysisDto sanitized = sanitize(parsed, Markets.currencyFor(input.market()))
                .withMeta(true, client.provider().name().toLowerCase(Locale.ROOT), prompt);

        double cost = usageTracker.estimateCostUsd(completion.inputTokens(), completion.outputTokens());
        usageTracker.complete(ownerKey, new AiUsageEvent(client.provider().name(), completion.model(), USE_CASE, ownerKey, tier,
                completion.inputTokens(), completion.outputTokens(), cost, true, false, Instant.now()));
        return sanitized;
    }

    private PlannerIntentAnalysisDto ruleBasedAnalyze(PlannerInputDto input, String prompt) {
        PlannerInputDto enriched = ruleBasedExtractor.enrich(input);
        // Confidence is deliberately moderate: the rule-based parser is reliable on the patterns it
        // knows but can't read intent it has no rule for.
        boolean hadPrompt = prompt != null && !prompt.isBlank();
        PlannerIntentAnalysisDto analysis = new PlannerIntentAnalysisDto(
                // Currency follows the market, not a hardcoded "EUR" — otherwise a rule-based FALLBACK in a
                // non-EUR market (GB/NO/SE/DK), which happens on any LLM hiccup or cap, mislabels the currency.
                enriched.roomType(), enriched.budget(), Markets.currencyFor(input.market()), enriched.size(), enriched.style(),
                enriched.preferredRetailers(), enriched.mustHaveCategories(), enriched.alreadyHaveCategories(),
                List.of(), enriched.colorPreferences(), enriched.materialPreferences(),
                qualityFromGoal(enriched.optimizationGoal(), enriched.furnishingLevel()), null,
                hadPrompt ? 0.6 : 0.4, List.of(), null, prompt, List.of(), false, "rule-based", false, Map.of());
        return analysis.withMeta(false, "rule-based", prompt);
    }

    /**
     * Maps a (sanitised) analysis onto a {@link PlannerInputDto} for the deterministic planner.
     * The prompt is cleared so {@code PlannerService.generateResolved} does not re-parse it — the AI
     * analysis is authoritative for this path.
     */
    public PlannerInputDto toPlannerInput(PlannerIntentAnalysisDto analysis, PlannerInputDto baseInput) {
        PlannerInputDto base = (baseInput == null ? new PlannerInputDto("", 0, null, null, null, 0, null, null, null,
                null, null, null, null, null, null, 0) : baseInput).normalized();

        // Sprint 10.169: an explicit non-default room selection from the UI wins over the room the AI merely
        // INFERRED from the prompt (the pre-filled example is a living-room, so picking Bathroom but leaving the
        // example was overridden to living-room). On the living-room default the AI-detected room still applies.
        // Bug 2026-07-10: only honor the structured room when it was DELIBERATELY chosen (roomInferred=false). The
        // frontend writes a generated plan's INFERRED room back into the form, so a following prompt ("Spavaća
        // soba…") arrived with roomType="bathroom" and the AI's fresh room was discarded — same stale-room bug as
        // the rule-based path. When roomInferred=true, the AI-detected room wins.
        String selectedRoom = base.roomType();
        boolean honorSelectedRoom = selectedRoom != null && !selectedRoom.isBlank()
                && !"living-room".equals(selectedRoom) && !base.roomInferred();
        String roomType = honorSelectedRoom
                ? selectedRoom
                : ProductTaxonomy.normalizeRoom(analysis.roomType()).orElse(selectedRoom);
        String style = analysis.style() != null && KNOWN_STYLES.contains(analysis.style().toLowerCase(Locale.ROOT))
                ? analysis.style().toLowerCase(Locale.ROOT) : base.style();
        int ceiling = Markets.budgetCeiling(Markets.currencyFor(base.market()));
        // Sprint 10.120: a full-room budget below ceiling/90 (~100€) is unrealistic so we floor it; but a FOCUSED
        // single-item request ("a lamp up to 60€") has a legitimately small budget — use a much lower floor so we
        // don't silently inflate the user's stated max and then blow past it.
        int budgetFloor = analysis.specificItemsOnly() ? Math.max(1, ceiling / 900) : Math.max(1, ceiling / 90);
        int budget = analysis.budget() == null ? base.budget() : clamp(analysis.budget(), budgetFloor, ceiling);
        int size = analysis.roomSize() == null ? base.size() : clamp(analysis.roomSize(), 8, 60);

        List<String> mustHave = validCategories(analysis.mustHaveCategories());
        Set<String> alreadyHave = new LinkedHashSet<>(validCategories(analysis.alreadyHaveCategories()));
        alreadyHave.addAll(validCategories(analysis.avoidCategories())); // avoid == don't add to the plan
        // Sprint 10.124: "I already have a bed" implies a usable bed — don't then sell an expensive mattress the
        // user never asked for. (Skipped if they explicitly listed a mattress as a must-have, handled below.)
        if (alreadyHave.contains("bed")) alreadyHave.add("mattress");
        alreadyHave.removeAll(mustHave);
        List<String> preferred = validRetailers(analysis.preferredRetailers());

        String optimizationGoal = goalFromQuality(analysis.qualityPreference(), base.optimizationGoal());
        String furnishingLevel = levelFromQuality(analysis.qualityPreference(), base.furnishingLevel());

        PlannerInputDto resolved = new PlannerInputDto(
                "", // prompt cleared on purpose; analysis is authoritative
                budget, roomType, style, base.location(), size, base.retailerMode(), base.selectedRetailers(),
                optimizationGoal, furnishingLevel, mustHave, new ArrayList<>(alreadyHave), base.lockedProductIds(),
                preferred, base.excludedRetailers(), base.maxStores(),
                lowerAll(analysis.colorPreferences()), lowerAll(analysis.materialPreferences()), base.market()
        ).normalized().withQuantities(validQuantities(analysis.quantities()));
        // Sprint 10.186: the LLM schema exposes only a SOFT preferredRetailers, so a hard "ne želim IKEA" / "samo
        // JYSK" in the prompt was lost on the AI path (the plan still returned IKEA). Re-derive the retailer intent
        // (exclude / only / store-count) from the original prompt and merge it in — the deterministic path already
        // does this inside enrich(), so this makes the two paths agree.
        return ruleBasedExtractor.applyRetailerIntentFromPrompt(base.prompt(), resolved);
    }

    // --- LLM prompt construction ---

    // Package-visible + static (pure function of the taxonomy) so a test can assert the allowed-category list stays
    // synchronized with ProductTaxonomy.canonicalCategories() (Phase 12).
    static String systemPrompt() {
        return "Ti si parser za BudgetSpace AI. Iz korisnikovog opisa opremanja sobe (na BILO KOJEM jeziku — "
                + "hrvatski, engleski, njemački, francuski, talijanski, nizozemski, španjolski, portugalski, "
                + "slovački, slovenski, finski, norveški, švedski, danski) izvuci STRUKTURIRANE podatke i vrati "
                + "ISKLJUČIVO JSON objekt (bez markdowna, bez teksta okolo). "
                + "Ne izmišljaj proizvode, cijene ni URL-ove — samo parametre planiranja.\n"
                + "Ključevi (koristi točno ove nazive): roomType, budget, currency, roomSize, style, preferredRetailers, "
                + "mustHaveCategories, alreadyHaveCategories, avoidCategories, colorPreferences, materialPreferences, "
                + "qualityPreference, urgency, confidence, missingImportantInfo, userGoalSummary, normalizedPrompt, warnings, specificItemsOnly, quantities.\n"
                + "quantities: objekt {kategorija: cijeli_broj} SAMO za komade kojih korisnik traži VIŠE od jednog "
                + "(npr. '6 stolica' → {\"dining-chair\":6}, '2 noćna ormarića' → {\"nightstand\":2}). Koristi kanonske "
                + "engleske nazive kategorija. Ako je količina 1 ili nije navedena, izostavi je.\n"
                + "specificItemsOnly: boolean — true ako korisnik traži SAMO konkretne komade koje je naveo "
                + "(npr. 'tražim dobar stol', 'samo lampa do 80', 'ormar za spavaću', '6 stolica'), a NE opremanje cijele sobe. "
                + "Kad je true, navedi te komade u mustHaveCategories. false ako želi opremiti cijelu sobu.\n"
                + "roomType MORA biti TOČNO jedna od ovih kanonskih engleskih vrijednosti: "
                + "[living-room, bedroom, home-office, kitchen, dining-room, hallway, bathroom, studio]. "
                + "PREVEDI korisnikovu riječ za sobu s bilo kojeg jezika u tu kanonsku vrijednost, npr.: "
                + "cuisine/Küche/kuhinja/kök/keittiö → kitchen; chambre/Schlafzimmer/spavaća soba/sovrum/dormitorio/quarto → bedroom; "
                + "salon/salotto/woonkamer/Wohnzimmer/dnevni boravak → living-room; bureau/oficina/radni kutak → home-office; "
                + "predsoblje/hodnik/Vorzimmer/Diele/Flur/hal/hall/vestibule/eteinen/entré/hol → hallway; "
                + "garsonijera/monolocale/Einzimmerwohnung/Garçonnière/yksiö/estudio/estúdio/etta/ettromsleilighet/"
                + "etværelses/'studio apartman'/'studio flat'/garsónka → studio (JEDNOSOBAN stan u kojem se i SPAVA i "
                + "boravi — NE home-office; obavezno uključi krevet). "
                + "cuina/cozinha → kitchen. Ako soba JEST spomenuta, OBAVEZNO je mapiraj u kanonsku vrijednost — "
                + "NE vraćaj living-room kao zamjenu za drugu spomenutu sobu. Ako soba uopće NIJE spomenuta, "
                + "koristi living-room kao zadanu.\n"
                + "style ∈ [bright, warm, modern, minimal, classic, industrial, boho, surprise].\n"
                // Sprint 10.181: the allowed-category list is GENERATED from the canonical taxonomy so it can never
                // again drift (bathroom fixtures + kitchen appliances were previously missing here, so the LLM could
                // not return them). See ProductTaxonomy.canonicalCategories() + PromptIntelligenceServiceTest.
                + "kategorije ∈ [" + String.join(", ", ProductTaxonomy.canonicalCategories()) + "].\n"
                + "textiles = zavjese, ukrasni jastuci, deke/pledovi/prekrivači (meki tekstil).\n"
                + "Kupaonske sanitarije: toilet = WC školjka, washbasin = umivaonik, bathtub = kada, shower = tuš / "
                + "tuš kabina (bath-shower = kombinirana tuš-kada). Ako korisnik traži TUŠ, koristi shower (NE bathtub); "
                + "ako traži KADU, koristi bathtub (NE shower); poštuj i isključenja ('bez kade', 'kadu neću').\n"
                + "Kuhinjski uređaji: oven = pećnica, hob = ploča za kuhanje, cooker-hood = napa, fridge = hladnjak, "
                + "freezer = zamrzivač, dishwasher = perilica posuđa, microwave = mikrovalna. kitchen-set = kompletna/"
                + "modularna kuhinja (cijela kuhinja kao jedan proizvod).\n"
                + "table = NISKI klub/dnevni stolić (coffee/side table — klub stolić, Couchtisch, soffbord, tavolino, "
                + "mesa de centro). dining-table = stol ZA JELO (Esstisch, matbord, mesa de comedor/jantar, tavolo da "
                + "pranzo, ruokapöytä, blagovaonski stol, spisebord, eettafel) — ako korisnik traži stol za "
                + "blagovanje/jelo/objedovanje, koristi dining-table (i dining-chair za stolice), NIKAD table.\n"
                + "retaileri ∈ [IKEA, JYSK]. qualityPreference ∈ [budget, balanced, premium]. "
                + "Izvuci budget i kad je naveden NEPRECIZNO: 'oko 6000 kr' / 'omkring 6000' / 'around 2000' / "
                + "'~1500 €' / 'do 600' / 'maks 800' / 'budžet bi bio nekih 1300' → uzmi taj broj kao budget. "
                + "'k'/'K' iza broja množi s 1000 (npr. '1k' = 1000, '2k' = 2000); 'tisuću'/'tisuća'/'tisuca' = 1000. "
                + "budget i roomSize su cijeli brojevi. budget vrati u valuti koju korisnik koristi (navedena niže) "
                + "kao GOLI broj, BEZ pretvaranja u drugu valutu (npr. 15000 kr ostaje 15000, ne pretvaraj u eure). "
                + "roomSize je u m². confidence je OBAVEZAN broj 0..1 — koliko si siguran u parsiranje: za jasan, "
                + "smislen opis opremanja vrati 0.8–1.0; za nejasan, prazan ili besmislen/nepovezan opis vrati < 0.4. "
                + "UVIJEK vrati confidence (nikad ga ne izostavi). "
                + "Ako neki DRUGI podatak nije naveden, izostavi ga ili stavi null, i dodaj u missingImportantInfo. "
                + "userGoalSummary napiši na ISTOM JEZIKU kojim korisnik piše (zrcali korisnikov jezik — to se "
                + "prikazuje korisniku). normalizedPrompt napiši na hrvatskom (interno).";
    }

    private String userPrompt(PlannerInputDto input, String prompt) {
        String currency = Markets.currencyFor(input.market());
        StringBuilder context = new StringBuilder();
        context.append("Valuta tržišta: ").append(currency)
                .append(" — budget vrati u ovoj valuti, bez pretvaranja.\n");
        if (input.roomType() != null) context.append("Korisnik je već odabrao sobu: ").append(input.roomType()).append("\n");
        if (input.budget() > 0) context.append("Već upisan budžet: ").append(input.budget()).append(" ").append(currency).append("\n");
        if (input.style() != null) context.append("Već odabran stil: ").append(input.style()).append("\n");
        return context + "Opis korisnika:\n" + (prompt == null || prompt.isBlank() ? "(prazno)" : prompt);
    }

    // --- sanitisation / mapping helpers ---

    private PlannerIntentAnalysisDto sanitize(PlannerIntentAnalysisDto a, String currency) {
        String roomType = ProductTaxonomy.normalizeRoom(a.roomType()).orElse(null);
        String style = a.style() != null && KNOWN_STYLES.contains(a.style().toLowerCase(Locale.ROOT))
                ? a.style().toLowerCase(Locale.ROOT) : null;
        int ceiling = Markets.budgetCeiling(currency);
        // Sprint 10.120: focused single-item requests have a legitimately small budget ("a lamp up to 60€"),
        // so don't floor them at ceiling/90 (~100) — that silently inflated the user's stated max.
        int budgetFloor = a.specificItemsOnly() ? Math.max(1, ceiling / 900) : Math.max(1, ceiling / 90);
        Integer budget = a.budget() == null ? null : clamp(a.budget(), budgetFloor, ceiling);
        Integer size = a.roomSize() == null ? null : clamp(a.roomSize(), 8, 60);
        String quality = a.qualityPreference() != null && QUALITY_PREFERENCES.contains(a.qualityPreference().toLowerCase(Locale.ROOT))
                ? a.qualityPreference().toLowerCase(Locale.ROOT) : null;
        List<String> validMustHave = validCategories(a.mustHaveCategories());
        // Sprint 10.186: recover the room from an unambiguous requested item when the model left it at the default.
        String resolvedRoom = inferRoomFromCategories(roomType, validMustHave);
        // "Specific items only" restricts the plan to the requested categories — but if NONE of them mapped to a
        // category we stock (e.g. "a hammock, a disco ball and a bean bag"), that would build an EMPTY plan. Drop
        // the restriction in that case so the user still gets a normal room instead of nothing.
        boolean specificItemsOnly = a.specificItemsOnly() && !validMustHave.isEmpty();
        return new PlannerIntentAnalysisDto(
                // Currency is decided by the market, never by the model — the LLM sometimes echoes a symbol
                // ("€") or the wrong code, so always use the authoritative market currency passed in here.
                resolvedRoom, budget, currency, size, style,
                validRetailers(a.preferredRetailers()), validMustHave,
                validCategories(a.alreadyHaveCategories()), validCategories(a.avoidCategories()),
                lowerAll(a.colorPreferences()), lowerAll(a.materialPreferences()),
                quality, a.urgency(), a.confidence(), a.missingImportantInfo(), safeEcho(a.userGoalSummary()),
                safeEcho(a.normalizedPrompt()), a.warnings(), a.aiUsed(), a.source(), specificItemsOnly, validQuantities(a.quantities()));
    }

    // Sprint 10.186: fill in the room from a specific requested item when the model left it at the living-room
    // default (or blank). Only acts on the default, only when the requested items point to exactly one other room,
    // and never when a living-room anchor (sofa/tv-unit) is present — so a genuine living-room request is untouched.
    private String inferRoomFromCategories(String roomType, List<String> categories) {
        if (roomType != null && !roomType.equals("living-room")) return roomType;
        if (categories == null || categories.isEmpty()) return roomType;
        if (categories.stream().anyMatch(LIVING_ROOM_ANCHORS::contains)) return roomType;
        LinkedHashSet<String> implied = new LinkedHashSet<>();
        for (String category : categories) {
            String room = ROOM_DEFINING_CATEGORY.get(category);
            if (room != null) implied.add(room);
        }
        return implied.size() == 1 ? implied.iterator().next() : roomType;
    }

    // Sprint 10.186: strip HTML/angle-bracket content and cap the length of a user-facing echo field.
    private String safeEcho(String text) {
        if (text == null) return null;
        String cleaned = text.replaceAll("<[^>]*>", "").replace("<", "").replace(">", "").strip();
        if (cleaned.length() > MAX_ECHO_LENGTH) cleaned = cleaned.substring(0, MAX_ECHO_LENGTH).strip() + "…";
        return cleaned;
    }

    private List<String> validCategories(List<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String value : values) {
            ProductTaxonomy.normalizeCategory(value).ifPresent(out::add);
        }
        return new ArrayList<>(out);
    }

    // Sprint 10.120: keep only known categories, drop counts < 1, clamp to a sane furniture maximum.
    private Map<String, Integer> validQuantities(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) return Map.of();
        LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
        values.forEach((key, count) -> {
            if (count == null || count < 1) return;
            ProductTaxonomy.normalizeCategory(key).ifPresent(cat -> out.put(cat, Math.min(count, 12)));
        });
        return out;
    }

    // Sprint 10.186: keep a preferred retailer only if it is (a) a known taxonomy retailer AND (b) actually STOCKED in
    // the catalog. normalizeRetailer alone accepts ~90 names incl. registered-but-empty slots (Wayfair), so a plain
    // taxonomy check would let an injected/unstocked store through; catalog presence is the honest central signal, and
    // it keeps legitimate third retailers (Emmezeta, Lesnina, …). Falls back to the taxonomy check only if the catalog
    // is unavailable — never widening beyond the supported set.
    private List<String> validRetailers(List<String> values) {
        if (values == null) return List.of();
        Set<String> stocked = stockedRetailers();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String value : values) {
            ProductTaxonomy.normalizeRetailer(value)
                    .filter(retailer -> stocked.isEmpty() || stocked.contains(retailer.toLowerCase(Locale.ROOT)))
                    .ifPresent(out::add);
        }
        return new ArrayList<>(out);
    }

    // The retailer names (lowercased) that actually have products in the catalog, cached with a short TTL.
    private Set<String> stockedRetailers() {
        Set<String> cached = stockedRetailersLower;
        long now = System.currentTimeMillis();
        if (cached != null && now - stockedComputedAtMs < STOCKED_TTL_MS) return cached;
        Set<String> fresh = new HashSet<>();
        if (productRepository != null) {
            for (Product product : productRepository.findAll()) {
                if (product.getRetailer() != null && !product.getRetailer().isBlank()) {
                    fresh.add(product.getRetailer().toLowerCase(Locale.ROOT));
                }
            }
        }
        stockedRetailersLower = fresh;
        stockedComputedAtMs = now;
        return fresh;
    }

    private List<String> lowerAll(List<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) out.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(out);
    }

    private String goalFromQuality(String quality, String fallback) {
        if (quality == null) return fallback;
        return switch (quality) {
            case "budget" -> "lowest-price";
            case "premium" -> "style-match";
            case "balanced" -> "best-value";
            default -> fallback;
        };
    }

    private String levelFromQuality(String quality, String fallback) {
        if (quality == null) return fallback;
        return switch (quality) {
            case "budget" -> "basic";
            case "premium" -> "complete";
            case "balanced" -> "comfort";
            default -> fallback;
        };
    }

    private String qualityFromGoal(String goal, String level) {
        if ("lowest-price".equals(goal) || "basic".equals(level)) return "budget";
        if ("style-match".equals(goal) || "complete".equals(level)) return "premium";
        return "balanced";
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String extractJson(String text) {
        if (text == null) return "{}";
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return text;
    }
}
