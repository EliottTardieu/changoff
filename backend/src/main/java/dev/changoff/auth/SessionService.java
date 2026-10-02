package dev.changoff.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SessionService {

    public static final Duration LIFETIME = Duration.ofDays(7);
    private final SessionRepository sessions;
    private final SecureRandom random = new SecureRandom();

    public SessionService(SessionRepository sessions) {
        this.sessions = sessions;
    }

    public UUID requireUser(String token) {
        return sessions
            .findUser(hash(token))
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in.")
            );
    }

    public String issue(UUID user, String previousToken) {
        sessions.clearExpiredAndPrevious(hash(previousToken));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // Persist only a digest; a database read must not reveal usable session cookies.
        sessions.insert(hash(token), user, Instant.now().plus(LIFETIME));
        return token;
    }

    public void revoke(String token) {
        sessions.delete(hash(token));
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for sessions", exception);
        }
    }
}
