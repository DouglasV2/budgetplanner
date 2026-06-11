package ai.budgetspace.saved;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedPlanRepository extends JpaRepository<SavedPlan, String> {
}
