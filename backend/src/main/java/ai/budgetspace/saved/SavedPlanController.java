package ai.budgetspace.saved;

import ai.budgetspace.dto.SavedPlanFavoriteRequest;
import ai.budgetspace.dto.SavedPlanRequest;
import ai.budgetspace.dto.SavedPlanResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SavedPlanController {
    private static final String SESSION_HEADER = "X-BudgetSpace-Session";

    private final SavedPlanService savedPlanService;

    public SavedPlanController(SavedPlanService savedPlanService) {
        this.savedPlanService = savedPlanService;
    }

    @PostMapping("/api/saved-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public SavedPlanResponse save(@RequestBody SavedPlanRequest request,
                                  @RequestHeader(name = SESSION_HEADER, required = false) String sessionId) {
        return savedPlanService.save(request, sessionId);
    }

    // Sprint 10.53: the "Moji planovi" inbox is scoped to the caller's session — only their own saved plans.
    @GetMapping("/api/saved-plans")
    public List<SavedPlanResponse> findAll(@RequestHeader(name = SESSION_HEADER, required = false) String sessionId) {
        return savedPlanService.findForSession(sessionId);
    }

    // Open by id on purpose: this is the shareable link (the id is the capability), so a recipient on another
    // session can still open a plan that was shared with them.
    @GetMapping("/api/saved-plans/{id}")
    public SavedPlanResponse findById(@PathVariable String id) {
        return savedPlanService.findById(id);
    }

    @PatchMapping("/api/saved-plans/{id}/favorite")
    public SavedPlanResponse setFavorite(@PathVariable String id, @RequestBody SavedPlanFavoriteRequest request,
                                         @RequestHeader(name = SESSION_HEADER, required = false) String sessionId) {
        return savedPlanService.setFavorite(id, request.favorite(), sessionId);
    }
}
