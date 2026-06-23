package ai.budgetspace.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 10.63 — the Google ID-token verifier is the security boundary of sign-in, so it is tested end to end:
 * a token signed by Google's key (here, our generated key advertised as the JWKS) passes; everything off — wrong
 * audience, wrong issuer, expired, a signature from a different key — is rejected. No network is used; the test
 * injects a JWKSource holding the public half of the key that signed the token.
 */
class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "client-123.apps.googleusercontent.com";

    private RSAKey signingKey;
    private GoogleTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("kid-1").generate();
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK()));
        verifier = new GoogleTokenVerifier(new AuthProperties(CLIENT_ID, 30, 7, false, "Lax"), source);
    }

    @Test
    void verifiesAGenuineGoogleToken() throws Exception {
        String token = sign(signingKey, baseClaims().build());

        GoogleTokenVerifier.GoogleIdentity identity = verifier.verify(token);

        assertThat(identity.sub()).isEqualTo("sub-1");
        assertThat(identity.email()).isEqualTo("ana@example.com");
        assertThat(identity.name()).isEqualTo("Ana");
        assertThat(identity.emailVerified()).isTrue();
    }

    @Test
    void acceptsTheBareIssuerSpelling() throws Exception {
        String token = sign(signingKey, baseClaims().issuer("accounts.google.com").build());
        assertThat(verifier.verify(token).sub()).isEqualTo("sub-1");
    }

    @Test
    void rejectsATokenForAnotherAudience() throws Exception {
        String token = sign(signingKey, baseClaims().audience("someone-else").build());
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void rejectsAnUnexpectedIssuer() throws Exception {
        String token = sign(signingKey, baseClaims().issuer("https://evil.example.com").build());
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void rejectsAnExpiredToken() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        String token = sign(signingKey, baseClaims()
                .issueTime(Date.from(past.minusSeconds(60)))
                .expirationTime(Date.from(past))
                .build());
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void rejectsATokenSignedByAnotherKey() throws Exception {
        RSAKey attackerKey = new RSAKeyGenerator(2048).keyID("kid-1").generate();
        String token = sign(attackerKey, baseClaims().build());
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void rejectsBlankAndGarbageTokens() {
        assertThatThrownBy(() -> verifier.verify("")).isInstanceOf(InvalidGoogleTokenException.class);
        assertThatThrownBy(() -> verifier.verify("not-a-jwt")).isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void refusesToVerifyWhenGoogleSignInIsNotConfigured() throws Exception {
        GoogleTokenVerifier dormant = new GoogleTokenVerifier(new AuthProperties("", 30, 7, false, "Lax"),
                new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK())));
        String token = sign(signingKey, baseClaims().build());
        assertThatThrownBy(() -> dormant.verify(token)).isInstanceOf(GoogleSignInUnavailableException.class);
    }

    private JWTClaimsSet.Builder baseClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .subject("sub-1")
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("email", "ana@example.com")
                .claim("name", "Ana")
                .claim("picture", "https://example.com/ana.png")
                .claim("email_verified", true);
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
