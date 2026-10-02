package dev.changoff.auth;

import static dev.changoff.account.AccountRequests.*;

import dev.changoff.account.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final AccountRepository accounts;
    private final SessionService sessions;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    // Keep the password work comparable for unknown accounts and incorrect passwords.
    private final String dummyHash = passwords.encode("not-a-real-password");

    public AuthService(AccountRepository accounts, SessionService sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    public record Authentication(AccountProfile profile, String token) {}

    @Transactional
    public Authentication register(Registration input, String previousToken) {
        if (
            input.password().getBytes(StandardCharsets.UTF_8).length > 72
        ) throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes.");
        UUID id = UUID.randomUUID();
        try {
            accounts.insert(
                id,
                input,
                normalizeEmail(input.email()),
                passwords.encode(input.password())
            );
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "An account with this email already exists."
            );
        }
        return new Authentication(accounts.profile(id), sessions.issue(id, previousToken));
    }

    @Transactional
    public Authentication login(Login input, String previousToken) {
        var credentials = accounts.findCredentials(normalizeEmail(input.email()));
        boolean matches = passwords.matches(
            input.password(),
            credentials.map(AccountRepository.Credentials::passwordHash).orElse(dummyHash)
        );
        if (credentials.isEmpty() || !matches) throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Email or password is incorrect."
        );
        UUID id = credentials.get().id();
        return new Authentication(accounts.profile(id), sessions.issue(id, previousToken));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
