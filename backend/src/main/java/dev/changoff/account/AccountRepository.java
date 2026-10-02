package dev.changoff.account;

import java.util.Optional;
import java.util.UUID;

/** Persistence contract; the JDBC implementation owns SQL and row mapping. */
public interface AccountRepository {
    public record Credentials(UUID id, String passwordHash) {}

    Optional<Credentials> findCredentials(String email);
    AccountProfile profile(UUID id);
    void insert(UUID id, AccountRequests.Registration input, String email, String passwordHash);
    void update(UUID id, AccountRequests.Profile input);
}
