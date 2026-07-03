package ai.budgetspace.tracking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface PlusInterestRepository extends JpaRepository<PlusInterest, String> {

    /**
     * GDPR Art. 17 erasure: drop every waitlist row carrying this email (case-insensitive). Called when the
     * account that owns this email is deleted. Returns the number of rows removed (for the audit log).
     */
    @Modifying
    @Query("delete from PlusInterest p where lower(p.email) = lower(:email)")
    int deleteByEmailIgnoreCase(@Param("email") String email);

    /**
     * Retention (GDPR Art. 5(1)(e) storage limitation): drop waitlist rows older than the cutoff — this table holds
     * an optional email (PII) that must not outlive any relationship. Returns the count removed (for the cleanup
     * log). Called by the scheduled {@link RetentionCleanupService}.
     */
    @Modifying
    @Transactional
    @Query("delete from PlusInterest p where p.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
