package dev.changoff.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence contract; the JDBC implementation owns SQL and row mapping. */
public interface SessionRepository {
    Optional<UUID> findUser(String hash);
    void clearExpiredAndPrevious(String previousHash);
    void insert(String hash, UUID user, Instant expiresAt);
    void delete(String hash);
}
