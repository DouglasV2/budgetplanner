package ai.budgetspace.auth;

import ai.budgetspace.auth.GoogleTokenVerifier.GoogleIdentity;
import ai.budgetspace.pricewatch.PriceWatchRepository;
import ai.budgetspace.saved.SavedPlanRepository;
import ai.budgetspace.tracking.PlusInterestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.63 — the account + session lifecycle: sign-in upserts the account and migrates the guest's plans;
 * sessions enforce absolute expiry and idle timeout (auto-logout) and slide on use; ownership resolves to the
 * account when signed in and to the guest session otherwise.
 */
class AuthServiceTest {

    private GoogleTokenVerifier verifier;
    private AppUserRepository userRepository;
    private AuthSessionRepository sessionRepository;
    private SavedPlanRepository savedPlanRepository;
    private PriceWatchRepository priceWatchRepository;
    private PlusInterestRepository plusInterestRepository;
    private AuthService service;

    @BeforeEach
    void setUp() {
        verifier = mock(GoogleTokenVerifier.class);
        userRepository = mock(AppUserRepository.class);
        sessionRepository = mock(AuthSessionRepository.class);
        savedPlanRepository = mock(SavedPlanRepository.class);
        priceWatchRepository = mock(PriceWatchRepository.class);
        plusInterestRepository = mock(PlusInterestRepository.class);
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new AuthService(verifier, userRepository, sessionRepository, savedPlanRepository,
                priceWatchRepository, plusInterestRepository,
                new AuthProperties("client-123", 30, 7, false, "Lax"));
    }

    @Test
    void loginCreatesAccountSessionAndMigratesGuestPlans() {
        when(verifier.verify("tok")).thenReturn(new GoogleIdentity("sub-1", "ana@example.com", "Ana", "pic", true));
        when(userRepository.findByGoogleSub("sub-1")).thenReturn(Optional.empty());

        AuthService.LoginResult result = service.login("tok", "guest-xyz");

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(userCaptor.capture());
        AppUser saved = userCaptor.getValue();
        assertThat(saved.getGoogleSub()).isEqualTo("sub-1");
        assertThat(saved.getName()).isEqualTo("Ana");
        // The guest's saved plans (owned under the "guest:" namespace) are re-owned onto the new account key.
        verify(savedPlanRepository).reassignOwner("guest:guest-xyz", "user:" + saved.getId());
        // A session is minted with a real token and an absolute expiry ~30 days out.
        assertThat(result.session().getToken()).isNotBlank();
        assertThat(result.session().getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(29)));
    }

    @Test
    void loginUpdatesAnExistingAccountProfile() {
        AppUser existing = new AppUser("u1", "sub-1", "old@example.com", "Old Name", "oldpic",
                Instant.now().minus(Duration.ofDays(10)), Instant.now().minus(Duration.ofDays(10)));
        when(verifier.verify("tok")).thenReturn(new GoogleIdentity("sub-1", "new@example.com", "New Name", "newpic", true));
        when(userRepository.findByGoogleSub("sub-1")).thenReturn(Optional.of(existing));

        AuthService.LoginResult result = service.login("tok", null);

        assertThat(result.user().getId()).isEqualTo("u1");
        assertThat(result.user().getName()).isEqualTo("New Name");
        assertThat(result.user().getEmail()).isEqualTo("new@example.com");
        // No guest session id → nothing to migrate.
        verify(savedPlanRepository, never()).reassignOwner(any(), any());
    }

    @Test
    void loginIgnoresAForgedAccountKeyAsGuestSessionId() {
        // Security: an attacker signs in with their OWN valid token but names a VICTIM's account key as the
        // guest source, trying to steal the victim's plans. The "user:" prefix is rejected → no migration runs.
        when(verifier.verify("tok")).thenReturn(new GoogleIdentity("sub-attacker", "a@evil.com", "A", "pic", true));
        when(userRepository.findByGoogleSub("sub-attacker")).thenReturn(Optional.empty());

        service.login("tok", "user:victim-id");

        verify(savedPlanRepository, never()).reassignOwner(any(), any());
    }

    @Test
    void invalidTokenIsPropagatedAndNothingIsPersisted() {
        when(verifier.verify("bad")).thenThrow(new InvalidGoogleTokenException("nope"));

        assertThatThrownBy(() -> service.login("bad", "guest-xyz")).isInstanceOf(InvalidGoogleTokenException.class);

        verify(userRepository, never()).save(any());
        verify(sessionRepository, never()).save(any());
        verify(savedPlanRepository, never()).reassignOwner(any(), any());
    }

    @Test
    void authenticateReturnsUserForALiveSession() {
        Instant now = Instant.now();
        AuthSession session = new AuthSession("tok", "u1", now.minus(Duration.ofDays(1)),
                now.plus(Duration.ofDays(29)), now.minusSeconds(30));
        when(sessionRepository.findById("tok")).thenReturn(Optional.of(session));
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));

        assertThat(service.authenticate("tok")).map(AppUser::getId).contains("u1");
        // lastSeenAt was just touched (within the throttle window) → no write.
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void authenticateExpiresAndPrunesAnExpiredSession() {
        Instant now = Instant.now();
        AuthSession session = new AuthSession("tok", "u1", now.minus(Duration.ofDays(40)),
                now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(1)));
        when(sessionRepository.findById("tok")).thenReturn(Optional.of(session));

        assertThat(service.authenticate("tok")).isEmpty();
        verify(sessionRepository).delete(session);
    }

    @Test
    void authenticatePrunesAnIdleSession() {
        Instant now = Instant.now();
        // Within absolute TTL, but untouched for longer than the 7-day idle limit.
        AuthSession session = new AuthSession("tok", "u1", now.minus(Duration.ofDays(10)),
                now.plus(Duration.ofDays(20)), now.minus(Duration.ofDays(8)));
        when(sessionRepository.findById("tok")).thenReturn(Optional.of(session));

        assertThat(service.authenticate("tok")).isEmpty();
        verify(sessionRepository).delete(session);
    }

    @Test
    void authenticateSlidesLastSeenWhenStale() {
        Instant now = Instant.now();
        AuthSession session = new AuthSession("tok", "u1", now.minus(Duration.ofDays(2)),
                now.plus(Duration.ofDays(28)), now.minus(Duration.ofHours(2)));
        when(sessionRepository.findById("tok")).thenReturn(Optional.of(session));
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));

        assertThat(service.authenticate("tok")).isPresent();
        // Outside the throttle window → lastSeenAt is bumped and persisted.
        verify(sessionRepository).save(session);
        assertThat(session.getLastSeenAt()).isAfter(now.minus(Duration.ofMinutes(1)));
    }

    @Test
    void resolveOwnerKeyPrefersAccountThenNamespacesGuestAndRejectsForgery() {
        Instant now = Instant.now();
        AuthSession session = new AuthSession("tok", "u1", now, now.plus(Duration.ofDays(29)), now);
        when(sessionRepository.findById("tok")).thenReturn(Optional.of(session));
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));

        // Signed in → the account key.
        assertThat(service.resolveOwnerKey("tok", "guest-xyz")).isEqualTo("user:u1");
        // Guest → the disjoint "guest:" namespace, so a guest value can never collide with an account key.
        assertThat(service.resolveOwnerKey(null, "guest-xyz")).isEqualTo("guest:guest-xyz");
        assertThat(service.resolveOwnerKey("   ", "guest-xyz")).isEqualTo("guest:guest-xyz");
        assertThat(service.resolveOwnerKey(null, null)).isNull();
        // Security (IDOR): a guest forging an account key in the header gets NO owner — never another account's.
        assertThat(service.resolveOwnerKey(null, "user:victim-id")).isNull();
    }

    @Test
    void logoutDeletesTheSession() {
        service.logout("tok");
        verify(sessionRepository).deleteById("tok");

        service.logout("  ");
        verify(sessionRepository, never()).deleteById("  ");
    }

    @Test
    void deleteAccountErasesPlansSessionsAndTheUser() {
        AppUser user = user("u9");

        service.deleteAccount(user);

        // GDPR erasure: the account's saved plans (owned under "user:<id>"), every session, then the account row.
        verify(savedPlanRepository).deleteByOwner("user:u9");
        verify(sessionRepository).deleteByUserId("u9");
        // ...and the email PII that lives outside the account row: price-drop watches + the Plus waitlist.
        verify(priceWatchRepository).deleteByEmailIgnoreCase("e@example.com");
        verify(plusInterestRepository).deleteByEmailIgnoreCase("e@example.com");
        verify(userRepository).delete(user);
    }

    @Test
    void deleteAccountStillErasesTheAccountWhenEmailIsMissing() {
        // Defensive: an account with no email (shouldn't happen via Google, but guard it) is still fully erased,
        // and we don't issue a blanket delete against the email-keyed tables.
        AppUser user = new AppUser("u10", "sub-u10", null, "Name", "pic", Instant.now(), Instant.now());

        service.deleteAccount(user);

        verify(priceWatchRepository, never()).deleteByEmailIgnoreCase(anyString());
        verify(plusInterestRepository, never()).deleteByEmailIgnoreCase(anyString());
        verify(userRepository).delete(user);
    }

    @Test
    void deleteAccountIsANoOpForNull() {
        service.deleteAccount(null);

        verify(savedPlanRepository, never()).deleteByOwner(any());
        verify(sessionRepository, never()).deleteByUserId(any());
        verify(priceWatchRepository, never()).deleteByEmailIgnoreCase(anyString());
        verify(plusInterestRepository, never()).deleteByEmailIgnoreCase(anyString());
        verify(userRepository, never()).delete(any());
    }

    private static AppUser user(String id) {
        return new AppUser(id, "sub-" + id, "e@example.com", "Name", "pic", Instant.now(), Instant.now());
    }
}
