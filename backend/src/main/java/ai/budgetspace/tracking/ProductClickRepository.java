package ai.budgetspace.tracking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface ProductClickRepository extends JpaRepository<ProductClick, Long> {

    /**
     * Retention (GDPR Art. 5(1)(e) storage limitation): drop click rows older than the cutoff. Returns the count
     * removed (for the cleanup log). Called by the scheduled {@link RetentionCleanupService}.
     */
    @Modifying
    @Transactional
    @Query("delete from ProductClick c where c.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
