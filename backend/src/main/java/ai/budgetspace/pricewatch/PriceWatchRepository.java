package ai.budgetspace.pricewatch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PriceWatchRepository extends JpaRepository<PriceWatch, String> {

    /** Active watches the re-check job evaluates (unsubscribed ones are skipped). */
    List<PriceWatch> findByActiveTrue();

    /** One-click unsubscribe lookup. */
    Optional<PriceWatch> findByUnsubscribeToken(String unsubscribeToken);

    /** Dedupe: a user can hold at most one active watch per product (case-insensitive email). */
    Optional<PriceWatch> findByEmailIgnoreCaseAndExternalIdAndActiveTrue(String email, String externalId);

    /**
     * GDPR Art. 17 erasure: drop every watch carrying this email (case-insensitive). Called when the account
     * that owns this email is deleted, so the subscriber's only PII does not outlive the account. Returns the
     * number of rows removed (for the audit log).
     */
    @Modifying
    @Query("delete from PriceWatch w where lower(w.email) = lower(:email)")
    int deleteByEmailIgnoreCase(@Param("email") String email);
}
