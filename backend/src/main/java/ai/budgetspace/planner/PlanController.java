package ai.budgetspace.planner;

import ai.budgetspace.dto.DesignAssistantResponse;
import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.PlannerIntentAnalysisDto;
import ai.budgetspace.dto.ReplaceProductRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlanController {
    private final PlannerService plannerService;
    private final DesignAssistantService designAssistantService;
    private final PromptIntelligenceService promptIntelligenceService;

    public PlanController(PlannerService plannerService, DesignAssistantService designAssistantService,
                         PromptIntelligenceService promptIntelligenceService) {
        this.plannerService = plannerService;
        this.designAssistantService = designAssistantService;
        this.promptIntelligenceService = promptIntelligenceService;
    }

    @PostMapping("/api/plans/generate")
    public PlanGenerationResponse generate(@RequestBody PlannerInputDto input,
                                           @RequestHeader(name = "X-BudgetSpace-Session", required = false) String sessionId) {
        // Sprint 10.10: understand the prompt first (AI when enabled, else rule-based), then plan.
        PlannerIntentAnalysisDto analysis = promptIntelligenceService.analyze(input, sessionId);
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
