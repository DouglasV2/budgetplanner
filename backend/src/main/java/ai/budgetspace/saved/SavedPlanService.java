package ai.budgetspace.saved;

import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.SavedPlanRequest;
import ai.budgetspace.dto.SavedPlanResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class SavedPlanService {
    private final SavedPlanRepository savedPlanRepository;
    private final ObjectMapper objectMapper;
    // Sprint 10.68: how many plans a Free-tier owner may save before Plus is required (Plus = unlimited).
    private final int freeSavedLimit;

    public SavedPlanService(SavedPlanRepository savedPlanRepository, ObjectMapper objectMapper,
                            @Value("${budgetspace.plus.free-saved-limit:3}") int freeSavedLimit) {
        this.savedPlanRepository = savedPlanRepository;
        this.objectMapper = objectMapper;
        this.freeSavedLimit = freeSavedLimit;
    }

    public SavedPlanResponse save(SavedPlanRequest request, String ownerKey, boolean plus) {
        if (request == null || request.plan() == null || request.input() == null) {
            throw new IllegalArgumentException("plan and input are required");
        }

        String owner = blankToNull(ownerKey);
        // Sprint 10.68: the Free tier caps how many plans an owner can save; Plus is unlimited. A null owner is a
        // save with no session (invisible to every inbox), so the cap doesn't apply to it.
        if (!plus && owner != null && savedPlanRepository.countBySessionId(owner) >= freeSavedLimit) {
            throw new PlanLimitReachedException(freeSavedLimit);
        }

        try {
            SavedPlan savedPlan = new SavedPlan(
                    shortId(),
                    objectMapper.writeValueAsString(request.plan()),
                    objectMapper.writeValueAsString(request.input().normalized()),
                    Instant.now()
            );
            // Sprint 10.53: stamp the owner so the inbox can be scoped to this session.
            savedPlan.setSessionId(owner);
            // Sprint 10.61: stamp the space (e.g. "Moj dom") so the inbox can group a home's rooms together.
            savedPlan.setSpaceName(blankToNull(request.spaceName()));
            return toResponse(savedPlanRepository.save(savedPlan));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not save plan", exception);
        }
    }

    /**
     * Open by id — this is the shareable link: the unguessable id is the capability, so a recipient on a
     * different session can still open a plan that was shared with them. (Personal listing is scoped; this is not.)
     */
    public SavedPlanResponse findById(String id) {
        return savedPlanRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new SavedPlanNotFoundException("Saved plan not found"));
    }

    /** The "Moji planovi" inbox — scoped to the owner's session. No session → no personal inbox (empty). */
    public List<SavedPlanResponse> findForSession(String sessionId) {
        String owner = blankToNull(sessionId);
        if (owner == null) return List.of();
        return savedPlanRepository.findBySessionIdOrderByFavoriteDescCreatedAtDesc(owner).stream()
                .map(this::toResponse)
                .toList();
    }

    public SavedPlanResponse setFavorite(String id, boolean favorite, String sessionId) {
        SavedPlan savedPlan = savedPlanRepository.findById(id)
                .orElseThrow(() -> new SavedPlanNotFoundException("Saved plan not found"));
        // Owner-only: only the session that saved a plan may toggle its favorite flag. A non-owner (incl. a
        // shared-link viewer) is treated as if it does not exist, so ownership is never revealed or mutated.
        String owner = blankToNull(sessionId);
        if (savedPlan.getSessionId() == null || !savedPlan.getSessionId().equals(owner)) {
            throw new SavedPlanNotFoundException("Saved plan not found");
        }
        savedPlan.setFavorite(favorite);
        return toResponse(savedPlanRepository.save(savedPlan));
    }

    private SavedPlanResponse toResponse(SavedPlan savedPlan) {
        try {
            return new SavedPlanResponse(
                    savedPlan.getId(),
                    objectMapper.readValue(savedPlan.getPlanJson(), FurnishingPlanDto.class),
                    objectMapper.readValue(savedPlan.getInputJson(), PlannerInputDto.class).normalized(),
                    savedPlan.getCreatedAt(),
                    savedPlan.isFavorite(),
                    savedPlan.getSpaceName()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read saved plan", exception);
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private String shortId() {
        // Sprint 10.67 (security audit): the share id is a capability — the /plan/<id> link is open-by-id with no
        // auth — so it must be unguessable. 16 CSPRNG bytes = 128 bits, URL-safe; replaces the old 40-bit UUID prefix.
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
