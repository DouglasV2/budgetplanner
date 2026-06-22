package ai.budgetspace.tracking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlusInterestRepository extends JpaRepository<PlusInterest, String> {

    /**
     * GDPR Art. 17 erasure: drop every waitlist row carrying this email (case-insensitive). Called when the
     * account that owns this email is deleted. Returns the number of rows removed (for the audit log).
     */
    @Modifying
    @Query("delete from PlusInterest p where lower(p.email) = lower(:email)")
    int deleteByEmailIgnoreCase(@Param("email") String email);
}
