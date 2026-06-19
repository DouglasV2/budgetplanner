package ai.budgetspace.saved;

import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.SavedPlanRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.53 — saved plans are scoped to the owner's browser session, closing the privacy leak where the
 * "Moji planovi" inbox returned EVERY visitor's plans. Proves: save stamps the owner; the inbox returns only
 * the caller's own plans (and nothing for no session); a shared link still opens by id for any session; and
 * only the owner may toggle favorite.
 */
class SavedPlanServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SavedPlanService serviceWith(SavedPlanRepository repository) {
        return new SavedPlanService(repository, objectMapper);
    }

    @Test
    void saveStampsTheOwnerSession() {
        SavedPlanRepository repository = mock(SavedPlanRepository.class);
        when(repository.save(any(SavedPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SavedPlanService service = serviceWith(repository);

        service.save(new SavedPlanRequest(planDto(), inputDto()), "session-A");

        ArgumentCaptor<SavedPlan> captor = ArgumentCaptor.forClass(SavedPlan.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSessionId()).isEqualTo("session-A");
    }

    @Test
    void inboxReturnsOnlyTheCallersOwnPlans() throws Exception {
        SavedPlanRepository repository = mock(SavedPlanRepository.class);
        when(repository.findBySessionIdOrderByFavoriteDescCreatedAtDesc("session-A"))
                .thenReturn(List.of(entity("plan-a", "session-A")));
        when(repository.findBySessionIdOrderByFavoriteDescCreatedAtDesc("session-B"))
                .thenReturn(List.of());
        SavedPlanService service = serviceWith(repository);

        assertThat(service.findForSession("session-A")).hasSize(1);
        assertThat(service.findForSession("session-B")).isEmpty();
        // No session → no personal inbox, and we never hit the DB with a null owner.
        assertThat(service.findForSession(null)).isEmpty();
        assertThat(service.findForSession("   ")).isEmpty();
        verify(repository, never()).findBySessionIdOrderByFavoriteDescCreatedAtDesc(null);
    }

    @Test
    void sharedLinkOpensByIdRegardlessOfSession() throws Exception {
        SavedPlanRepository repository = mock(SavedPlanRepository.class);
        when(repository.findById("plan-a")).thenReturn(Optional.of(entity("plan-a", "session-A")));
        SavedPlanService service = serviceWith(repository);

        // A recipient on a different (or no) session can still open a plan shared with them by its id.
        assertThat(service.findById("plan-a").id()).isEqualTo("plan-a");
    }

    @Test
    void onlyTheOwnerCanToggleFavorite() throws Exception {
        SavedPlanRepository repository = mock(SavedPlanRepository.class);
        when(repository.findById("plan-a")).thenReturn(Optional.of(entity("plan-a", "session-A")));
        when(repository.save(any(SavedPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SavedPlanService service = serviceWith(repository);

        assertThat(service.setFavorite("plan-a", true, "session-A").favorite()).isTrue();
        assertThatThrownBy(() -> service.setFavorite("plan-a", true, "session-B"))
                .isInstanceOf(SavedPlanNotFoundException.class);
        assertThatThrownBy(() -> service.setFavorite("plan-a", true, null))
                .isInstanceOf(SavedPlanNotFoundException.class);
    }

    @Test
    void findByIdThrowsNotFoundForAMissingPlan() {
        SavedPlanRepository repository = mock(SavedPlanRepository.class);
        when(repository.findById("gone")).thenReturn(Optional.empty());
        SavedPlanService service = serviceWith(repository);

        // A stale shared /plan/<id> link → a clean not-found (the global handler maps this to 404, not 500).
        assertThatThrownBy(() -> service.findById("gone")).isInstanceOf(SavedPlanNotFoundException.class);
    }

    @Test
    void saveStampsTheSpaceName() {
        SavedPlanRepository repository = mock(SavedPlanRepository.class);
        when(repository.save(any(SavedPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SavedPlanService service = serviceWith(repository);

        var response = service.save(new SavedPlanRequest(planDto(), inputDto(), "Moj dom"), "session-A");

        ArgumentCaptor<SavedPlan> captor = ArgumentCaptor.forClass(SavedPlan.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSpaceName()).isEqualTo("Moj dom");
        assertThat(response.spaceName()).isEqualTo("Moj dom");
    }

    private SavedPlan entity(String id, String sessionId) throws Exception {
        SavedPlan plan = new SavedPlan(
                id,
                objectMapper.writeValueAsString(planDto()),
                objectMapper.writeValueAsString(inputDto()),
                Instant.now());
        plan.setSessionId(sessionId);
        return plan;
    }

    private FurnishingPlanDto planDto() {
        return new FurnishingPlanDto(
                "value", "Najbolji izbor", "label", "desc", "sum", "good", "trade", "status", "note", "step",
                List.of(), List.of(), List.of(), BigDecimal.valueOf(100), BigDecimal.ZERO, 80, "Low", 90,
                List.of(), null, List.of(), List.of(), BigDecimal.ZERO, null);
    }

    private PlannerInputDto inputDto() {
        return new PlannerInputDto("p", 1500, "living-room", "modern", "Zagreb", 20, "multi", List.of("IKEA"),
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0);
    }
}
