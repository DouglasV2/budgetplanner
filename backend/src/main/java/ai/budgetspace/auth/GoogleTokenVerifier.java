package ai.budgetspace.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Set;

/**
 * Sprint 10.63 — verifies a Google Sign-In ID token locally, the way Google documents for production:
 *
 * <ul>
 *   <li><strong>Signature</strong> against Google's published RSA keys (JWKS, fetched + cached by Nimbus), so no
 *       per-login call to a Google verification endpoint and no rate limit.</li>
 *   <li><strong>Audience</strong> equals our OAuth client id — the token was minted for THIS app.</li>
 *   <li><strong>Issuer</strong> is {@code accounts.google.com} (with or without the {@code https://}).</li>
 *   <li><strong>Expiry</strong> is enforced (Nimbus checks {@code exp}/{@code nbf} with a small clock skew).</li>
 * </ul>
 *
 * <p>No client secret is involved — that is only for the server-side authorization-code flow, which we do not
 * use. Any failure throws {@link InvalidGoogleTokenException}; we never trust an unverified token.</p>
 */
@Component
public class GoogleTokenVerifier {

    private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ALLOWED_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final AuthProperties properties;
    private volatile JWKSource<SecurityContext> jwkSource;

    // @Autowired marks the constructor Spring uses — the class also has a test-only two-arg constructor, and
    // with multiple constructors Spring needs to be told which one (otherwise it looks for a no-arg one).
    @Autowired
    public GoogleTokenVerifier(AuthProperties properties) {
        this.properties = properties;
    }

    // Package-private: lets a test inject a JWKSource backed by its own generated key, so the full verification
    // path runs without any network call to Google.
    GoogleTokenVerifier(AuthProperties properties, JWKSource<SecurityContext> jwkSource) {
        this.properties = properties;
        this.jwkSource = jwkSource;
    }

    /** The verified identity carried by a Google ID token. */
    public record GoogleIdentity(String sub, String email, String name, String pictureUrl, boolean emailVerified) {
    }

    /**
     * Verifies the ID token and returns the identity it carries, or throws {@link InvalidGoogleTokenException}.
     */
    public GoogleIdentity verify(String idToken) {
        if (!properties.googleEnabled()) {
            throw new GoogleSignInUnavailableException("Google prijava nije konfigurirana (nema client id).");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new InvalidGoogleTokenException("Prazan Google token.");
        }

        JWTClaimsSet claims;
        try {
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource()));
            // Required audience = our client id; required claims must be present; exp/nbf checked by default.
            processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                    properties.googleClientId(),
                    new JWTClaimsSet.Builder().build(),
                    Set.of("sub", "iat", "exp")));
            claims = processor.process(idToken, null);
        } catch (InvalidGoogleTokenException exception) {
            throw exception;
        } catch (Exception exception) {
            // Bad signature / wrong audience / expired / malformed — all rejected with one generic message.
            throw new InvalidGoogleTokenException("Google token nije valjan.", exception);
        }

        // Google issues the token with one of two issuer spellings; DefaultJWTClaimsVerifier can only exact-match
        // one, so we check the issuer here against both accepted values.
        if (!ALLOWED_ISSUERS.contains(claims.getIssuer())) {
            throw new InvalidGoogleTokenException("Neočekivani izdavatelj Google tokena.");
        }

        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new InvalidGoogleTokenException("Google token bez subjekta.");
        }
        return new GoogleIdentity(
                sub,
                stringClaim(claims, "email"),
                stringClaim(claims, "name"),
                stringClaim(claims, "picture"),
                Boolean.TRUE.equals(booleanClaim(claims, "email_verified")));
    }

    private JWKSource<SecurityContext> jwkSource() {
        JWKSource<SecurityContext> local = jwkSource;
        if (local == null) {
            synchronized (this) {
                local = jwkSource;
                if (local == null) {
                    try {
                        // Nimbus caches the key set and refreshes it on rotation; no fetch happens until verify.
                        local = JWKSourceBuilder.create(URI.create(GOOGLE_JWKS_URL).toURL()).build();
                    } catch (MalformedURLException exception) {
                        throw new IllegalStateException("Neispravan Google JWKS URL", exception);
                    }
                    jwkSource = local;
                }
            }
        }
        return local;
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (java.text.ParseException exception) {
            return null;
        }
    }

    private static Boolean booleanClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getBooleanClaim(name);
        } catch (java.text.ParseException exception) {
            return null;
        }
    }
}
