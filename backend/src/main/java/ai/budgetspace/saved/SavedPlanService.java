package ai.budgetspace.saved;

import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.SavedPlanRequest;
import ai.budgetspace.dto.SavedPlanResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SavedPlanService {
    private final SavedPlanRepository savedPlanRepository;
    private final ObjectMapper objectMapper;

    public SavedPlanService(SavedPlanRepository savedPlanRepository, ObjectMapper objectMapper) {
        this.savedPlanRepository = savedPlanRepository;
        this.objectMapper = objectMapper;
    }

    public SavedPlanResponse save(SavedPlanRequest request, String sessionId) {
        if (request == null || request.plan() == null || request.input() == null) {
            throw new IllegalArgumentException("plan and input are required");
        }

        try {
            SavedPlan savedPlan = new SavedPlan(
                    shortId(),
                    objectMapper.writeValueAsString(request.plan()),
                    objectMapper.writeValueAsString(request.input().normalized()),
                    Instant.now()
            );
            // Sprint 10.53: stamp the owner so the inbox can be scoped to this session.
            savedPlan.setSessionId(blankToNull(sessionId));
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

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
