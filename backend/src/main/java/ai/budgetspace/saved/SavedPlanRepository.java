package ai.budgetspace.saved;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedPlanRepository extends JpaRepository<SavedPlan, String> {
    List<SavedPlan> findAllByOrderByFavoriteDescCreatedAtDesc();
}
