package ai.budgetspace.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectorRunRepository extends JpaRepository<CollectorRun, String> {
    List<CollectorRun> findTop50ByOrderByCreatedAtDesc();
}
