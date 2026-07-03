package ai.budgetspace.auth;

import ai.budgetspace.ai.AiUsageRecordRepository;
import ai.budgetspace.ai.AiUsageTracker;
import ai.budgetspace.auth.GoogleTokenVerifier.GoogleIdentity;
import ai.budgetspace.pricewatch.PriceWatchRepository;
import ai.budgetspace.saved.SavedPlanRepository;
import ai.budgetspace.tracking.PlusInterestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 10.63 — the account + session lifecycle behind Google Sign-In.
 *
 * <ul>
 *   <li><strong>login</strong>: verify the Google ID token, upsert the account by its Google sub, migrate the
 *       visitor's guest-saved plans onto the account, and mint a server session.</li>
 *   <li><strong>authenticate</strong>: resolve a session token to its user, enforcing absolute expiry and idle
 *       timeout (auto-logout) and sliding {@code lastSeenAt} on use; a dead session is pruned and treated as
 *       logged out.</li>
 *   <li><strong>resolveOwnerKey</strong>: the single place that decides who owns saved plans — the signed-in
 *       account when present, else the guest's browser-session id (so guests keep working unchanged).</li>
 * </ul>
 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // Only persist a slid lastSeenAt at most this often, so a logged-in user does not cause a DB write on every
    // request. Idle detection is then accurate to within this window — far finer than the multi-day idle limit.
    private static final Duration SLIDE_THROTTLE = Duration.ofHours(1);

    // Saved-plan ownership lives in two DISJOINT namespaces so a guest-supplied value can never act as an
    // account key. An account owns plans under "user:<id>" (AppUser.ownerKey); a guest under "guest:<browserId>".
    private static final String ACCOUNT_PREFIX = "user:";
    private static final String GUEST_PREFIX = "guest:";

    private final GoogleTokenVerifier verifier;
    private final AppUserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final SavedPlanRepository savedPlanRepository;
    private final PriceWatchRepository priceWatchRepository;
    private final PlusInterestRepository plusInterestRepository;
    private final AiUsageRecordRepository aiUsageRecordRepository;
    private final AiUsageTracker aiUsageTracker;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public AuthService(GoogleTokenVerifier verifier,
                       AppUserRepository userRepository,
                       AuthSessionRepository sessionRepository,
                       SavedPlanRepository savedPlanRepository,
                       PriceWatchRepository priceWatchRepository,
                       PlusInterestRepository plusInterestRepository,
                       AiUsageRecordRepository aiUsageRecordRepository,
                       AiUsageTracker aiUsageTracker,
                       AuthProperties properties) {
        this.verifier = verifier;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.savedPlanRepository = savedPlanRepository;
        this.priceWatchRepository = priceWatchRepository;
        this.plusInterestRepository = plusInterestRepository;
        this.aiUsageRecordRepository = aiUsageRecordRepository;
        this.aiUsageTracker = aiUsageTracker;
        this.properties = properties;
    }

    /** The outcome of a successful sign-in: the account and the freshly minted session (cookie value + expiry). */
    public record LoginResult(AppUser user, AuthSession session) {
    }

    @Transactional
    public LoginResult login(String idToken, String guestSessionId) {
        GoogleIdentity identity = verifier.verify(idToken); // throws InvalidGoogleTokenException on any failure
        Instant now = Instant.now();

        AppUser user = userRepository.findByGoogleSub(identity.sub())
                .map(existing -> {
                    // Refresh the profile on each login — name/picture can change on the Google side.
                    existing.setEmail(identity.email());
                    existing.setName(identity.name());
                    existing.setPictureUrl(identity.pictureUrl());
                    existing.setLastLoginAt(now);
                    return existing;
                })
                .orElseGet(() -> new AppUser(newId(), identity.sub(), identity.email(), identity.name(),
                        identity.pictureUrl(), now, now));
        user = userRepository.save(user);

        // First sign-in on this browser: claim the plans the visitor saved as a guest (owned under the "guest:"
        // namespace) so nothing is lost. A guestSessionId that forges the account prefix ("user:...") is a
        // takeover attempt — ignored — so a caller can never name another account as the migration source and
        // steal its plans. (The prefix makes the source disjoint from any account key by construction.)
        String guestKey = blankToNull(guestSessionId);
        if (guestKey != null && !guestKey.startsWith(ACCOUNT_PREFIX)) {
            savedPlanRepository.reassignOwner(GUEST_PREFIX + guestKey, user.ownerKey());
        }

        AuthSession session = new AuthSession(
                newToken(), user.getId(), now,
                now.plus(Duration.ofDays(properties.sessionTtlDays())), now);
        session = sessionRepository.save(session);
        return new LoginResult(user, session);
    }

    /**
     * Resolves a session token to its user, or empty when there is no valid session. A session that is past its
     * absolute expiry or idle limit is deleted and treated as logged out; a live one slides {@code lastSeenAt}.
     */
    @Transactional
    public Optional<AppUser> authenticate(String sessionToken) {
        String token = blankToNull(sessionToken);
        if (token == null) {
            return Optional.empty();
        }
        AuthSession session = sessionRepository.findById(token).orElse(null);
        if (session == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        Instant idleCutoff = now.minus(Duration.ofDays(properties.sessionIdleDays()));
        if (session.getExpiresAt().isBefore(now) || session.getLastSeenAt().isBefore(idleCutoff)) {
            sessionRepository.delete(session); // expired or idle → prune + logged out
            return Optional.empty();
        }
        if (session.getLastSeenAt().isBefore(now.minus(SLIDE_THROTTLE))) {
            session.setLastSeenAt(now);
            sessionRepository.save(session);
        }
        return userRepository.findById(session.getUserId());
    }

    /**
     * The owner key for saved plans. A signed-in account owns plans under {@code user:<id>}; a guest owns them
     * under the disjoint {@code guest:<browserId>} namespace. An unauthenticated caller can NEVER land in the
     * account namespace: a blank guest value, or one forging the {@code user:} prefix, yields no owner (an empty
     * inbox) — so the {@code X-BudgetSpace-Session} header cannot be used to read or mutate another account's plans.
     *
     * <p>{@code @Transactional} so the inner {@link #authenticate} self-call (load/prune/slide) runs in one
     * transaction on this — the most-used — path, rather than being a no-op proxy self-invocation.</p>
     */
    @Transactional
    public String resolveOwnerKey(String sessionToken, String guestHeader) {
        return authenticate(sessionToken).map(AppUser::ownerKey).orElseGet(() -> {
            String guest = blankToNull(guestHeader);
            if (guest == null || guest.startsWith(ACCOUNT_PREFIX)) return null;
            return GUEST_PREFIX + guest;
        });
    }

    /**
     * Sprint 10.68: is the owner of these plans on the paid Plus tier? Only a signed-in account ({@code user:<id>})
     * can be Plus; a guest owner key is always Free. Used to gate Plus-only limits (e.g. unlimited saved plans).
     */
    public boolean isPlusOwner(String ownerKey) {
        if (ownerKey == null || !ownerKey.startsWith(ACCOUNT_PREFIX)) {
            return false;
        }
        return userRepository.findById(ownerKey.substring(ACCOUNT_PREFIX.length()))
                .map(AppUser::isPlus)
                .orElse(false);
    }

    /**
     * Sprint 10.70: the AI usage tier for an owner key — the account's plan (FREE/PLUS/PRO) when signed in,
     * else GUEST. A guest owner key (or none) is always GUEST, which carries the smallest daily AI allowance.
     */
    public String aiTierFor(String ownerKey) {
        if (ownerKey == null || !ownerKey.startsWith(ACCOUNT_PREFIX)) {
            return "GUEST";
        }
        return userRepository.findById(ownerKey.substring(ACCOUNT_PREFIX.length()))
                .map(AppUser::getPlan)
                .map(plan -> plan == null || plan.isBlank() ? "FREE" : plan.toUpperCase(Locale.ROOT))
                .orElse("GUEST");
    }

    @Transactional
    public void logout(String sessionToken) {
        String token = blankToNull(sessionToken);
        if (token != null) {
            sessionRepository.deleteById(token);
        }
    }

    /**
     * Sprint 10.72 — GDPR erasure ("right to be forgotten"). Permanently removes everything tied to this account:
     * the plans they saved (owned under {@code user:<id>}), all of their sessions, and the account row itself.
     * The controller clears the session cookie afterwards.
     *
     * <p>Sprint 10.73: the controller cancels any live Stripe subscription (best-effort) BEFORE calling this, so a
     * deleted account is never billed again.</p>
     *
     * <p>Sprint 10.81: GDPR Art. 17 also erases the account's email from the price-drop watches and the Plus
     * waitlist (matched case-insensitively by email — the only other places that store it), so no PII of the
     * deleted user survives. These are the only entities besides the account row that carry an email.</p>
     *
     * <p>Sprint 10.163: it also drops the account's rows in the AI-usage ledger ({@code ai_usage_events}, keyed by
     * the {@code user:<id>} owner key) — durable usage metadata (tier/model/tokens) that would otherwise outlive the
     * account — plus the transient in-memory usage counters for that owner in this instance. So "everything tied to
     * this account" is now literally true.</p>
     */
    @Transactional
    public void deleteAccount(AppUser user) {
        if (user == null) {
            return;
        }
        savedPlanRepository.deleteByOwner(user.ownerKey());
        sessionRepository.deleteByUserId(user.getId());
        // GDPR Art. 17: also erase the account's rows in the durable AI-usage ledger (keyed by the "user:<id>"
        // owner key), then drop this owner's transient in-memory usage counters — so no per-account usage
        // metadata survives the erasure, matching the "everything tied to this account" promise.
        int aiUsage = aiUsageRecordRepository.deleteByOwnerKey(user.ownerKey());
        aiUsageTracker.forgetOwner(user.ownerKey());
        // GDPR Art. 17: the account's email also lives in price-drop watches and the Plus waitlist — erase those
        // by email too, else the subscriber's only PII outlives the account they just deleted.
        int watches = 0;
        int interests = 0;
        String email = user.getEmail();
        if (email != null && !email.isBlank()) {
            watches = priceWatchRepository.deleteByEmailIgnoreCase(email);
            interests = plusInterestRepository.deleteByEmailIgnoreCase(email);
        }
        userRepository.delete(user);
        log.info("Account deleted (GDPR erasure): user={}, priceWatches={}, plusInterest={}, aiUsageEvents={}.",
                user.getId(), watches, interests, aiUsage);
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
