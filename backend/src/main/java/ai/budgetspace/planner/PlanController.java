package ai.budgetspace.planner;

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

    public PlanController(PlannerService plannerService) {
        this.plannerService = plannerService;
    }

    @PostMapping("/api/plans/generate")
    public PlanGenerationResponse generate(@RequestBody PlannerInputDto input) {
        return plannerService.generate(input);
    }

    @PostMapping("/api/plans/replace")
    public FurnishingPlanDto replaceProduct(@RequestBody ReplaceProductRequest request) {
        return plannerService.replaceProduct(request);
    }
}
