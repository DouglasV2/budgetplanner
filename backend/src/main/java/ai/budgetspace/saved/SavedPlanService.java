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
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class SavedPlanService {
    private final SavedPlanRepository savedPlanRepository;
    private final ObjectMapper objectMapper;

    public SavedPlanService(SavedPlanRepository savedPlanRepository, ObjectMapper objectMapper) {
        this.savedPlanRepository = savedPlanRepository;
        this.objectMapper = objectMapper;
    }

    public SavedPlanResponse save(SavedPlanRequest request) {
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
            return toResponse(savedPlanRepository.save(savedPlan));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not save plan", exception);
        }
    }

    public SavedPlanResponse findById(String id) {
        return savedPlanRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new NoSuchElementException("Saved plan not found"));
    }

    public List<SavedPlanResponse> findAll() {
        return savedPlanRepository.findAllByOrderByFavoriteDescCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public SavedPlanResponse setFavorite(String id, boolean favorite) {
        SavedPlan savedPlan = savedPlanRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Saved plan not found"));
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
                    savedPlan.isFavorite()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read saved plan", exception);
        }
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
