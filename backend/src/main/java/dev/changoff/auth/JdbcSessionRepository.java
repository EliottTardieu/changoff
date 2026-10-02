package dev.changoff.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSessionRepository implements SessionRepository {

    private final JdbcTemplate db;

    public JdbcSessionRepository(JdbcTemplate db) {
        this.db = db;
    }

    public Optional<UUID> findUser(String hash) {
        return db
            .queryForList(
                "SELECT user_id FROM sessions WHERE token_hash=? AND expires_at>CURRENT_TIMESTAMP",
                UUID.class,
                hash
            )
            .stream()
            .findFirst();
    }

    public void clearExpiredAndPrevious(String previousHash) {
        db.update(
            "DELETE FROM sessions WHERE expires_at<CURRENT_TIMESTAMP OR token_hash=?",
            previousHash
        );
    }

    public void insert(String hash, UUID user, Instant expiresAt) {
        db.update(
            "INSERT INTO sessions VALUES (?,?,?)",
            hash,
            user,
            java.sql.Timestamp.from(expiresAt)
        );
    }

    public void delete(String hash) {
        db.update("DELETE FROM sessions WHERE token_hash=?", hash);
    }
}
