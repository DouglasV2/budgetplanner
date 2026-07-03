package ai.budgetspace.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Durable AI-usage ledger (Sprint 10.86): write-through on each real call, read back on startup. */
public interface AiUsageRecordRepository extends JpaRepository<AiUsageRecord, Long> {

    /**
     * The most recent real events since {@code start} (this month), newest first and bounded, to rehydrate the
     * in-memory counters on boot. Bounded because the in-memory log is bounded; only the current month (wallet)
     * and today (daily caps) are ever counted.
     */
    List<AiUsageRecord> findTop5000ByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant start);

    /** Cleanup: rows older than the retention cutoff are never counted again — prune them. */
    @Modifying
    @Transactional
    @Query("delete from AiUsageRecord r where r.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);

    /**
     * GDPR Art. 17 erasure: drop every AI-usage ledger row owned by this account ({@code user:<id>}). Called when
     * the account is deleted, so no ledger metadata (tier/model/tokens keyed by owner) outlives the account.
     * Returns the number of rows removed (for the audit log).
     */
    @Modifying
    @Transactional
    @Query("delete from AiUsageRecord r where r.ownerKey = :ownerKey")
    int deleteByOwnerKey(@Param("ownerKey") String ownerKey);
}
