package ai.budgetspace.saved;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SavedPlanRepository extends JpaRepository<SavedPlan, String> {
    // Sprint 10.53: the inbox is scoped to the owner's session so a visitor never sees other people's plans.
    // (The old unscoped "find everything" query was removed — it leaked every visitor's plans into every inbox.)
    List<SavedPlan> findBySessionIdOrderByFavoriteDescCreatedAtDesc(String sessionId);

    // Sprint 10.68: how many plans this owner has saved — used to enforce the Free-tier saved-plan cap.
    long countBySessionId(String sessionId);

    // Sprint 10.63: on first Google sign-in, re-own the visitor's guest-saved plans (keyed by their browser
    // session id) onto their account key (user:<id>), so signing in never loses the plans they already made.
    @Modifying
    @Query("update SavedPlan s set s.sessionId = :newOwner where s.sessionId = :oldOwner")
    int reassignOwner(@Param("oldOwner") String oldOwner, @Param("newOwner") String newOwner);
}
