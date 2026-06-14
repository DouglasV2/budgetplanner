package ai.budgetspace.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectorRunItemRepository extends JpaRepository<CollectorRunItem, String> {
    List<CollectorRunItem> findByRunId(String runId);
}
