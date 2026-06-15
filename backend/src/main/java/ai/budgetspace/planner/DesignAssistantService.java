package ai.budgetspace.planner;

import ai.budgetspace.dto.DesignAssistantResponse;
import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlannerInputDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sprint 10.8 — first phase of the LLM-powered design assistant.
 *
 * <p>For now this is a deterministic, rule-based stub: it turns an already-generated plan into a
 * short, human-friendly description (room, budget, included categories, style and store mix). It
 * does <strong>not</strong> call any external model yet, so it adds no latency, cost or new
 * dependency, and the frontend integration ({@code POST /api/plans/design}) can ship and be tested
 * against a stable contract.</p>
 *
 * <p>The output shape ({@link DesignAssistantResponse}) is intentionally the same one a real model
 * would fill, so swapping the body of {@link #describe(PlanGenerationResponse)} for an LLM call is
 * a localised change — see the TODO in that method.</p>
 */
@Service
public class DesignAssistantService {
    private static final Logger log = LoggerFactory.getLogger(DesignAssistantService.class);

    private static final Map<String, String> ROOM_LABELS = Map.ofEntries(
            Map.entry("living-room", "dnevni boravak"),
            Map.entry("home-office", "radni kutak"),
            Map.entry("bedroom", "spavaća soba"),
            Map.entry("home-gym", "kućna teretana"),
            Map.entry("kitchen", "kuhinja"),
            Map.entry("dining-room", "blagovaonica"),
            Map.entry("hallway", "hodnik"),
            Map.entry("bathroom", "kupaonica")
    );

    private static final Map<String, String> STYLE_LABELS = Map.ofEntries(
            Map.entry("bright", "svijetao i prozračan"),
            Map.entry("warm", "topao i ugodan"),
            Map.entry("cozy", "topao i ugodan"),
            Map.entry("modern", "moderan"),
            Map.entry("minimal", "minimalan"),
            Map.entry("scandinavian", "skandinavski"),
            Map.entry("classic", "klasičan"),
            Map.entry("industrial", "industrijski"),
            Map.entry("boho", "boho"),
            Map.entry("surprise", "prepušten našem prijedlogu")
    );

    private static final Map<String, String> COLOR_LABELS = Map.ofEntries(
            Map.entry("white", "bijela"),
            Map.entry("black", "crna"),
            Map.entry("grey", "siva"),
            Map.entry("beige", "bež"),
            Map.entry("brown", "smeđa"),
            Map.entry("green", "zelena"),
            Map.entry("blue", "plava"),
            Map.entry("yellow", "žuta"),
            Map.entry("red", "crvena"),
            Map.entry("pink", "roza"),
            Map.entry("natural", "prirodna"),
            Map.entry("gold", "zlatna")
    );

    private static final Map<String, String> MATERIAL_LABELS = Map.ofEntries(
            Map.entry("wood", "drvo"),
            Map.entry("metal", "metal"),
            Map.entry("glass", "staklo"),
            Map.entry("fabric", "tekstil"),
            Map.entry("leather", "koža"),
            Map.entry("rattan", "ratan"),
            Map.entry("marble", "mramor"),
            Map.entry("velvet", "baršun")
    );

    public DesignAssistantResponse describe(PlanGenerationResponse plan) {
        // TODO (LLM integration): replace the rule-based text below with a call to Claude/GPT.
        //   Build a prompt from `input` (room, budget, style, colour/material preferences) and the
        //   chosen `primary` plan (items, prices, retailers), ask the model for a short Croatian
        //   design summary + 3-4 highlights, and map the response onto DesignAssistantResponse.
        //   Keep this rule-based version as the offline/dev fallback and on model error.
        if (plan == null || plan.input() == null) {
            return new DesignAssistantResponse(
                    "Još nemam dovoljno podataka za opis plana. Prvo složi plan, pa ću ga ovdje ukratko opisati.",
                    List.of());
        }

        PlannerInputDto input = plan.input();
        FurnishingPlanDto primary = primaryPlan(plan.plans());
        String room = ROOM_LABELS.getOrDefault(input.roomType(), input.roomType());
        String budget = money(input.budget());

        if (primary == null || primary.items().isEmpty()) {
            String summary = "Za " + room + " s budžetom do " + budget
                    + " € još nemamo dovoljno proizvoda u katalogu za konkretan prijedlog. "
                    + "Pokušaj povećati budžet ili ukloniti ograničenje trgovine.";
            logSummary(input, primary, 0);
            return new DesignAssistantResponse(summary, List.of());
        }

        List<String> categories = primary.items().stream()
                .map(item -> categoryLabel(item.product().category()))
                .distinct()
                .toList();
        String categoryList = joinHuman(categories);
        String style = STYLE_LABELS.getOrDefault(input.style(), input.style());

        StringBuilder summary = new StringBuilder()
                .append("Za ").append(room)
                .append(" s budžetom do ").append(budget).append(" € složili smo plan „")
                .append(primary.name()).append("” s ").append(primary.items().size())
                .append(" ").append(productsWord(primary.items().size())).append(" (").append(categoryList).append(").")
                .append(" Stil je ").append(style).append(prefClause(input)).append(".")
                .append(" ").append(storeClause(primary)).append(" Ukupna procjena je ")
                .append(money(primary.total())).append(" € (").append(budgetFitClause(input, primary)).append(").");

        List<String> highlights = buildHighlights(input, primary, plan);
        logSummary(input, primary, primary.items().size());
        return new DesignAssistantResponse(summary.toString(), highlights);
    }

    private List<String> buildHighlights(PlannerInputDto input, FurnishingPlanDto primary, PlanGenerationResponse plan) {
        List<String> highlights = new ArrayList<>();
        String coreItems = primary.items().stream()
                .filter(item -> "buy-first".equals(item.shoppingPriority()))
                .map(item -> item.product().name())
                .limit(3)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        if (!coreItems.isBlank()) {
            highlights.add("Glavni komadi: " + coreItems + ".");
        }
        highlights.add("Ukupno " + money(primary.total()) + " € · " + budgetFitClause(input, primary) + ".");
        if (primary.retailersUsed() != null && !primary.retailersUsed().isEmpty()) {
            highlights.add("Trgovine: " + String.join(" + ", primary.retailersUsed()) + ".");
        }
        String prefs = preferenceHighlight(input);
        if (prefs != null) highlights.add(prefs);
        if (plan.partialPlan() && plan.catalogWarning() != null) {
            highlights.add(plan.catalogWarning());
        }
        return highlights;
    }

    private FurnishingPlanDto primaryPlan(List<FurnishingPlanDto> plans) {
        if (plans == null || plans.isEmpty()) return null;
        return plans.stream()
                .filter(plan -> "Najbolji izbor".equals(plan.name()) || "value".equals(plan.id()))
                .findFirst()
                .orElse(plans.get(0));
    }

    private String prefClause(PlannerInputDto input) {
        String labels = preferenceLabels(input);
        return labels == null ? "" : ", a tražene boje/materijali su " + labels;
    }

    private String preferenceHighlight(PlannerInputDto input) {
        String labels = preferenceLabels(input);
        return labels == null ? null : "Boje/materijali koje si tražio: " + labels + ".";
    }

    private String preferenceLabels(PlannerInputDto input) {
        List<String> labels = new ArrayList<>();
        if (input.colorPreferences() != null) {
            input.colorPreferences().forEach(color -> labels.add(COLOR_LABELS.getOrDefault(color, color)));
        }
        if (input.materialPreferences() != null) {
            input.materialPreferences().forEach(material -> labels.add(MATERIAL_LABELS.getOrDefault(material, material)));
        }
        return labels.isEmpty() ? null : joinHuman(labels);
    }

    private String storeClause(FurnishingPlanDto primary) {
        List<String> retailers = primary.retailersUsed();
        if (retailers == null || retailers.isEmpty()) return "Kupnja je iz jedne trgovine.";
        if (retailers.size() == 1) return "Sve kupuješ u " + retailers.get(0) + ".";
        return "Kupnja kombinira " + String.join(" i ", retailers) + ".";
    }

    private String budgetFitClause(PlannerInputDto input, FurnishingPlanDto primary) {
        return primary.total().doubleValue() <= input.budget() ? "unutar budžeta" : "malo iznad budžeta";
    }

    private String categoryLabel(String category) {
        return PlannerReadiness.categoryLabel(category);
    }

    private String joinHuman(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        if (values.size() == 1) return values.get(0);
        return String.join(", ", values.subList(0, values.size() - 1)) + " i " + values.get(values.size() - 1);
    }

    private String productsWord(int count) {
        int mod10 = count % 10;
        int mod100 = count % 100;
        if (mod10 == 1 && mod100 != 11) return "proizvodom";
        if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) return "proizvoda";
        return "proizvoda";
    }

    private String money(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String money(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private void logSummary(PlannerInputDto input, FurnishingPlanDto primary, int itemCount) {
        String total = primary == null ? "0" : money(primary.total());
        log.info("Design assistant: room={}, budget={} EUR, items={}, total={} EUR (rule-based stub).",
                input.roomType(), input.budget(), itemCount, total);
    }
}
