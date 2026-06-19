package ai.budgetspace.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

    /** Housekeeping: drop expired/idle sessions so the table does not grow without bound. */
    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :now or s.lastSeenAt < :idleCutoff")
    int deleteExpired(@Param("now") Instant now, @Param("idleCutoff") Instant idleCutoff);
}
