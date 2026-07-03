package ai.budgetspace.tracking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface PlanFeedbackRepository extends JpaRepository<PlanFeedback, Long> {

    /**
     * Retention (GDPR Art. 5(1)(e) storage limitation): drop feedback rows older than the cutoff. Returns the count
     * removed (for the cleanup log). Called by the scheduled {@link RetentionCleanupService}.
     */
    @Modifying
    @Transactional
    @Query("delete from PlanFeedback f where f.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
