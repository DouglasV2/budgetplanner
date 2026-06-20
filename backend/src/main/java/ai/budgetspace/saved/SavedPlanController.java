package ai.budgetspace.saved;

import ai.budgetspace.auth.AuthService;
import ai.budgetspace.dto.SavedPlanFavoriteRequest;
import ai.budgetspace.dto.SavedPlanRequest;
import ai.budgetspace.dto.SavedPlanResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
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
    // Sprint 10.63: the auth session cookie set by AuthController (kept in sync with AuthController.COOKIE).
    private static final String AUTH_COOKIE = "bs_auth";

    private final SavedPlanService savedPlanService;
    private final AuthService authService;

    public SavedPlanController(SavedPlanService savedPlanService, AuthService authService) {
        this.savedPlanService = savedPlanService;
        this.authService = authService;
    }

    @PostMapping("/api/saved-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public SavedPlanResponse save(@RequestBody SavedPlanRequest request,
                                  @RequestHeader(name = SESSION_HEADER, required = false) String sessionId,
                                  @CookieValue(name = AUTH_COOKIE, required = false) String authToken) {
        // Sprint 10.68: resolve the owner once, then enforce the Free saved-plan cap (Plus owners are unlimited).
        String owner = authService.resolveOwnerKey(authToken, sessionId);
        return savedPlanService.save(request, owner, authService.isPlusOwner(owner));
    }

    // Sprint 10.53: the "Moji planovi" inbox is scoped to the caller — only their own saved plans. Sprint 10.63:
    // the owner is the signed-in account when present, else the guest browser-session id (resolveOwnerKey).
    @GetMapping("/api/saved-plans")
    public List<SavedPlanResponse> findAll(@RequestHeader(name = SESSION_HEADER, required = false) String sessionId,
                                           @CookieValue(name = AUTH_COOKIE, required = false) String authToken) {
        return savedPlanService.findForSession(authService.resolveOwnerKey(authToken, sessionId));
    }

    // Open by id on purpose: this is the shareable link (the id is the capability), so a recipient on another
    // session can still open a plan that was shared with them.
    @GetMapping("/api/saved-plans/{id}")
    public SavedPlanResponse findById(@PathVariable String id) {
        return savedPlanService.findById(id);
    }

    @PatchMapping("/api/saved-plans/{id}/favorite")
    public SavedPlanResponse setFavorite(@PathVariable String id, @RequestBody SavedPlanFavoriteRequest request,
                                         @RequestHeader(name = SESSION_HEADER, required = false) String sessionId,
                                         @CookieValue(name = AUTH_COOKIE, required = false) String authToken) {
        return savedPlanService.setFavorite(id, request.favorite(), authService.resolveOwnerKey(authToken, sessionId));
    }
}
