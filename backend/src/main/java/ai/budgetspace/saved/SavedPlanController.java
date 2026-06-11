package ai.budgetspace.saved;

import ai.budgetspace.dto.SavedPlanRequest;
import ai.budgetspace.dto.SavedPlanResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SavedPlanController {
    private final SavedPlanService savedPlanService;

    public SavedPlanController(SavedPlanService savedPlanService) {
        this.savedPlanService = savedPlanService;
    }

    @PostMapping("/api/saved-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public SavedPlanResponse save(@RequestBody SavedPlanRequest request) {
        return savedPlanService.save(request);
    }

    @GetMapping("/api/saved-plans/{id}")
    public SavedPlanResponse findById(@PathVariable String id) {
        return savedPlanService.findById(id);
    }
}
