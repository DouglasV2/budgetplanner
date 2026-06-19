package ai.budgetspace.saved;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedPlanRepository extends JpaRepository<SavedPlan, String> {
    // Sprint 10.53: the inbox is scoped to the owner's session so a visitor never sees other people's plans.
    // (The old unscoped "find everything" query was removed — it leaked every visitor's plans into every inbox.)
    List<SavedPlan> findBySessionIdOrderByFavoriteDescCreatedAtDesc(String sessionId);
}
