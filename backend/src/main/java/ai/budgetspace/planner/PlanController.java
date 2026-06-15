package ai.budgetspace.planner;

import ai.budgetspace.dto.DesignAssistantResponse;
import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.ReplaceProductRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlanController {
    private final PlannerService plannerService;
    private final DesignAssistantService designAssistantService;

    public PlanController(PlannerService plannerService, DesignAssistantService designAssistantService) {
        this.plannerService = plannerService;
        this.designAssistantService = designAssistantService;
    }

    @PostMapping("/api/plans/generate")
    public PlanGenerationResponse generate(@RequestBody PlannerInputDto input) {
        return plannerService.generate(input);
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
