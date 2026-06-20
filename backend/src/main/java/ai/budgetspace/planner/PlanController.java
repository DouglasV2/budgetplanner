package ai.budgetspace.planner;

import ai.budgetspace.dto.DesignAssistantResponse;
import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.auth.AuthService;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.PlannerIntentAnalysisDto;
import ai.budgetspace.dto.ReplaceProductRequest;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlanController {
    private static final String AUTH_COOKIE = "bs_auth";
    private static final String SESSION_HEADER = "X-BudgetSpace-Session";

    private final PlannerService plannerService;
    private final DesignAssistantService designAssistantService;
    private final PromptIntelligenceService promptIntelligenceService;
    private final AuthService authService;

    public PlanController(PlannerService plannerService, DesignAssistantService designAssistantService,
                         PromptIntelligenceService promptIntelligenceService, AuthService authService) {
        this.plannerService = plannerService;
        this.designAssistantService = designAssistantService;
        this.promptIntelligenceService = promptIntelligenceService;
        this.authService = authService;
    }

    @PostMapping("/api/plans/generate")
    public PlanGenerationResponse generate(@RequestBody PlannerInputDto input,
                                           @RequestHeader(name = SESSION_HEADER, required = false) String sessionId,
                                           @CookieValue(name = AUTH_COOKIE, required = false) String authToken) {
        // Sprint 10.70: resolve who's asking so the AI is gated by their tier's daily allowance. The owner key
        // ("user:<id>" signed-in, else "guest:<browserId>") is the counting identity; the tier (FREE/PLUS/PRO,
        // else GUEST) sets the per-day cap. A blank/forged session falls back to a per-browser guest bucket.
        String ownerKey = authService.resolveOwnerKey(authToken, sessionId);
        String tier = authService.aiTierFor(ownerKey);
        String countingKey = ownerKey != null ? ownerKey
                : "guest:" + (sessionId == null || sessionId.isBlank() ? "anon" : sessionId.trim());

        // Understand the prompt first (AI when enabled + within the tier's allowance, else rule-based), then plan.
        PlannerIntentAnalysisDto analysis = promptIntelligenceService.analyze(input, countingKey, tier);
        PlanGenerationResponse response = analysis.aiUsed()
                ? plannerService.generateResolved(promptIntelligenceService.toPlannerInput(analysis, input))
                : plannerService.generate(input);
        return response.withAnalysis(analysis);
    }

    @PostMapping("/api/plans/replace")
    public FurnishingPlanDto replaceProduct(@RequestBody ReplaceProductRequest request) {
        return plannerService.replaceProduct(request);
    }

    // Sprint 10.8: the frontend calls this after /api/plans/generate, passing the generation result
    // back in, to get a short design-assistant description of the plan.
    @PostMapping("/api/plans/design")
    public DesignAssistantResponse design(@RequestBody PlanGenerationResponse plan) {
        return designAssistantService.describe(plan);
    }
}
