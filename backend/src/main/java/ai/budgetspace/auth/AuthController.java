package ai.budgetspace.auth;

import ai.budgetspace.dto.AuthMeResponse;
import ai.budgetspace.dto.AuthUserDto;
import ai.budgetspace.dto.GoogleLoginRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Sprint 10.63 — Google Sign-In + session endpoints. The session is an opaque, HttpOnly, SameSite=Lax cookie, so
 * it is never readable by JavaScript (XSS can't steal it) and rides along on same-site requests to the API.
 */
@RestController
public class AuthController {

    /** The session cookie name. HttpOnly + SameSite=Lax; Secure is toggled per environment. */
    static final String COOKIE = "bs_auth";

    private final AuthService authService;
    private final AuthProperties properties;

    public AuthController(AuthService authService, AuthProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    /** Sign in with a Google ID token. Sets the session cookie and returns the signed-in profile. */
    @PostMapping("/api/auth/google")
    public AuthUserDto google(@RequestBody GoogleLoginRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(
                request == null ? null : request.credential(),
                request == null ? null : request.guestSessionId());
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(result.session().getToken(),
                Duration.ofDays(properties.sessionTtlDays())).toString());
        return toDto(result.user());
    }

    /** Who is signed in (or null), plus whether Google sign-in is available and the public client id to render it. */
    @GetMapping("/api/auth/me")
    public AuthMeResponse me(@CookieValue(name = COOKIE, required = false) String sessionToken) {
        AuthUserDto user = authService.authenticate(sessionToken).map(AuthController::toDto).orElse(null);
        return new AuthMeResponse(user, properties.googleEnabled(),
                properties.googleEnabled() ? properties.googleClientId() : null);
    }

    /** Sign out: delete the server session and clear the cookie. */
    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = COOKIE, required = false) String sessionToken, HttpServletResponse response) {
        authService.logout(sessionToken);
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private static AuthUserDto toDto(AppUser user) {
        return new AuthUserDto(user.getId(), user.getEmail(), user.getName(), user.getPictureUrl(), user.getPlan());
    }

    // A rejected Google token is a 401; we never say which check failed.
    @ExceptionHandler(InvalidGoogleTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String onInvalidToken(InvalidGoogleTokenException exception) {
        return "Google prijava nije uspjela.";
    }

    // Google sign-in attempted while it is not configured → unavailable, not a client error. A NARROW exception
    // type (so an unrelated internal IllegalStateException is never mislabeled 503) and a FIXED message (so no
    // internal detail leaks — other errors fall through to GlobalExceptionHandler's safe generic 500).
    @ExceptionHandler(GoogleSignInUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String onNotConfigured(GoogleSignInUnavailableException exception) {
        return "Google prijava trenutno nije dostupna.";
    }
}
