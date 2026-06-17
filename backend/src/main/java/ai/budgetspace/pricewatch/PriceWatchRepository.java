package ai.budgetspace.pricewatch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceWatchRepository extends JpaRepository<PriceWatch, String> {

    /** Active watches the re-check job evaluates (unsubscribed ones are skipped). */
    List<PriceWatch> findByActiveTrue();

    /** One-click unsubscribe lookup. */
    Optional<PriceWatch> findByUnsubscribeToken(String unsubscribeToken);

    /** Dedupe: a user can hold at most one active watch per product (case-insensitive email). */
    Optional<PriceWatch> findByEmailIgnoreCaseAndExternalIdAndActiveTrue(String email, String externalId);
}
