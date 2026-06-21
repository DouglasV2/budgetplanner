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
import ai.budgetspace.product.ProductTaxonomy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String USE_CASE = "intent-extraction";

    private static final Set<String> KNOWN_STYLES = Set.of(
            "bright", "warm", "modern", "minimal", "classic", "industrial", "boho", "surprise");
    private static final Set<String> QUALITY_PREFERENCES = Set.of("budget", "balanced", "premium");

    private final LlmClientFactory clientFactory;
    private final LlmProperties properties;
    private final AiUsageTracker usageTracker;
    private final PlannerIntentExtractor ruleBasedExtractor = new PlannerIntentExtractor();

    public PromptIntelligenceService(LlmClientFactory clientFactory, LlmProperties properties, AiUsageTracker usageTracker) {
        this.clientFactory = clientFactory;
        this.properties = properties;
        this.usageTracker = usageTracker;
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
                enriched.roomType(), enriched.budget(), "EUR", enriched.size(), enriched.style(),
                enriched.preferredRetailers(), enriched.mustHaveCategories(), enriched.alreadyHaveCategories(),
                List.of(), enriched.colorPreferences(), enriched.materialPreferences(),
                qualityFromGoal(enriched.optimizationGoal(), enriched.furnishingLevel()), null,
                hadPrompt ? 0.6 : 0.4, List.of(), null, prompt, List.of(), false, "rule-based");
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

        String roomType = ProductTaxonomy.normalizeRoom(analysis.roomType()).orElse(base.roomType());
        String style = analysis.style() != null && KNOWN_STYLES.contains(analysis.style().toLowerCase(Locale.ROOT))
                ? analysis.style().toLowerCase(Locale.ROOT) : base.style();
        int ceiling = Markets.budgetCeiling(Markets.currencyFor(base.market()));
        int budget = analysis.budget() == null ? base.budget() : clamp(analysis.budget(), Math.max(1, ceiling / 90), ceiling);
        int size = analysis.roomSize() == null ? base.size() : clamp(analysis.roomSize(), 8, 60);

        List<String> mustHave = validCategories(analysis.mustHaveCategories());
        Set<String> alreadyHave = new LinkedHashSet<>(validCategories(analysis.alreadyHaveCategories()));
        alreadyHave.addAll(validCategories(analysis.avoidCategories())); // avoid == don't add to the plan
        alreadyHave.removeAll(mustHave);
        List<String> preferred = validRetailers(analysis.preferredRetailers());

        String optimizationGoal = goalFromQuality(analysis.qualityPreference(), base.optimizationGoal());
        String furnishingLevel = levelFromQuality(analysis.qualityPreference(), base.furnishingLevel());

        return new PlannerInputDto(
                "", // prompt cleared on purpose; analysis is authoritative
                budget, roomType, style, base.location(), size, base.retailerMode(), base.selectedRetailers(),
                optimizationGoal, furnishingLevel, mustHave, new ArrayList<>(alreadyHave), base.lockedProductIds(),
                preferred, base.excludedRetailers(), base.maxStores(),
                lowerAll(analysis.colorPreferences()), lowerAll(analysis.materialPreferences()), base.market()
        ).normalized();
    }

    // --- LLM prompt construction ---

    private String systemPrompt() {
        return "Ti si parser za BudgetSpace AI. Iz korisnikovog opisa opremanja sobe (na BILO KOJEM jeziku — "
                + "hrvatski, engleski, njemački, francuski, talijanski, nizozemski, španjolski, portugalski, "
                + "slovački, slovenski, finski, norveški, švedski, danski) izvuci STRUKTURIRANE podatke i vrati "
                + "ISKLJUČIVO JSON objekt (bez markdowna, bez teksta okolo). "
                + "Ne izmišljaj proizvode, cijene ni URL-ove — samo parametre planiranja.\n"
                + "Ključevi (koristi točno ove nazive): roomType, budget, currency, roomSize, style, preferredRetailers, "
                + "mustHaveCategories, alreadyHaveCategories, avoidCategories, colorPreferences, materialPreferences, "
                + "qualityPreference, urgency, confidence, missingImportantInfo, userGoalSummary, normalizedPrompt, warnings.\n"
                + "roomType MORA biti TOČNO jedna od ovih kanonskih engleskih vrijednosti: "
                + "[living-room, bedroom, home-office, home-gym, kitchen, dining-room, hallway, bathroom]. "
                + "PREVEDI korisnikovu riječ za sobu s bilo kojeg jezika u tu kanonsku vrijednost, npr.: "
                + "cuisine/Küche/kuhinja/kök/keittiö → kitchen; chambre/Schlafzimmer/spavaća soba/sovrum/dormitorio/quarto → bedroom; "
                + "salon/salotto/woonkamer/Wohnzimmer/dnevni boravak → living-room; bureau/oficina/radni kutak/studio → home-office; "
                + "cuina/cozinha → kitchen. Ako soba JEST spomenuta, OBAVEZNO je mapiraj u kanonsku vrijednost — "
                + "NE vraćaj living-room kao zamjenu za drugu spomenutu sobu. Ako soba uopće NIJE spomenuta, "
                + "koristi living-room kao zadanu.\n"
                + "style ∈ [bright, warm, modern, minimal, classic, industrial, boho, surprise].\n"
                + "kategorije ∈ [sofa, tv-unit, table, rug, lighting, storage, decor, bed, mattress, desk, chair, "
                + "gym-equipment, dining-table, dining-chair, kitchen-storage, kitchen-cart, nightstand, wardrobe, dresser].\n"
                + "retaileri ∈ [IKEA, JYSK]. qualityPreference ∈ [budget, balanced, premium]. "
                + "budget i roomSize su cijeli brojevi. budget vrati u valuti koju korisnik koristi (navedena niže) "
                + "kao GOLI broj, BEZ pretvaranja u drugu valutu (npr. 15000 kr ostaje 15000, ne pretvaraj u eure). "
                + "roomSize je u m². confidence je broj 0..1. "
                + "Ako nešto nije navedeno, izostavi ili stavi null, i dodaj u missingImportantInfo. "
                + "userGoalSummary i normalizedPrompt napiši na hrvatskom.";
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
        Integer budget = a.budget() == null ? null : clamp(a.budget(), Math.max(1, ceiling / 90), ceiling);
        Integer size = a.roomSize() == null ? null : clamp(a.roomSize(), 8, 60);
        String quality = a.qualityPreference() != null && QUALITY_PREFERENCES.contains(a.qualityPreference().toLowerCase(Locale.ROOT))
                ? a.qualityPreference().toLowerCase(Locale.ROOT) : null;
        return new PlannerIntentAnalysisDto(
                roomType, budget, a.currency(), size, style,
                validRetailers(a.preferredRetailers()), validCategories(a.mustHaveCategories()),
                validCategories(a.alreadyHaveCategories()), validCategories(a.avoidCategories()),
                lowerAll(a.colorPreferences()), lowerAll(a.materialPreferences()),
                quality, a.urgency(), a.confidence(), a.missingImportantInfo(), a.userGoalSummary(),
                a.normalizedPrompt(), a.warnings(), a.aiUsed(), a.source());
    }

    private List<String> validCategories(List<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String value : values) {
            ProductTaxonomy.normalizeCategory(value).ifPresent(out::add);
        }
        return new ArrayList<>(out);
    }

    private List<String> validRetailers(List<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String value : values) {
            ProductTaxonomy.normalizeRetailer(value).ifPresent(out::add);
        }
        return new ArrayList<>(out);
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
