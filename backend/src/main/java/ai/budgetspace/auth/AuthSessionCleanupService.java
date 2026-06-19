package ai.budgetspace.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Sprint 10.63 — periodically prunes dead sessions (past their absolute expiry or idle limit) whose owner never
 * came back. The lazy prune inside {@link AuthService#authenticate} only fires when a token is re-presented, so
 * abandoned sessions would otherwise accumulate; this daily sweep keeps {@code auth_sessions} bounded. Runs only
 * because the app has {@code @EnableScheduling}; the cron is overridable via env.
 */
@Service
public class AuthSessionCleanupService {
    private static final Logger log = LoggerFactory.getLogger(AuthSessionCleanupService.class);

    private final AuthSessionRepository sessionRepository;
    private final AuthProperties properties;

    public AuthSessionCleanupService(AuthSessionRepository sessionRepository, AuthProperties properties) {
        this.sessionRepository = sessionRepository;
        this.properties = properties;
    }

    @Scheduled(cron = "${budgetspace.auth.session.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void pruneDeadSessions() {
        Instant now = Instant.now();
        Instant idleCutoff = now.minus(Duration.ofDays(properties.sessionIdleDays()));
        int removed = sessionRepository.deleteExpired(now, idleCutoff);
        if (removed > 0) {
            log.info("Auth session cleanup: pruned {} expired/idle session(s).", removed);
        }
    }
}
