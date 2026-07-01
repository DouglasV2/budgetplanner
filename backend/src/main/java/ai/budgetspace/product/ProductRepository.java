package ai.budgetspace.product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {

    /**
     * Sprint 10.156 — the STALEST products first, for the rolling catalog-freshness re-check.
     * {@code lastCheckedAt} is an ISO date/time string, so a lexicographic ASC sort is chronological
     * (oldest checked come first). Paged so each scheduled run only re-fetches a bounded batch.
     */
    List<Product> findByOrderByLastCheckedAtAsc(Pageable pageable);
    /**
     * Find a product by its external identifier.
     *
     * <p>When importing product data from external sources this method is
     * used to de-duplicate entries. If a product with the supplied
     * {@code externalId} exists the importer will update that record rather than
     * creating a new one.</p>
     *
     * @param externalId the external identifier provided by the source system
     * @return an optional containing the matching product or empty if none found
     */
    Optional<Product> findByExternalId(String externalId);
}
