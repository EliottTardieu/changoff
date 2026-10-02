package dev.changoff.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAccountRepository implements AccountRepository {

    private final JdbcTemplate db;

    public JdbcAccountRepository(JdbcTemplate db) {
        this.db = db;
    }

    public Optional<Credentials> findCredentials(String email) {
        return db
            .query(
                "SELECT id,password_hash FROM athletes WHERE email=?",
                (row, index) ->
                    new Credentials(
                        row.getObject("id", UUID.class),
                        row.getString("password_hash")
                    ),
                email
            )
            .stream()
            .findFirst();
    }

    public AccountProfile profile(UUID id) {
        return db.queryForObject(
            "SELECT id,name,email,bodyweight,standard,ranks_enabled FROM athletes WHERE id=?",
            (row, index) ->
                new AccountProfile(
                    row.getObject("id", UUID.class),
                    row.getString("name"),
                    row.getString("email"),
                    row.getDouble("bodyweight"),
                    row.getString("standard"),
                    row.getBoolean("ranks_enabled")
                ),
            id
        );
    }

    public void insert(
        UUID id,
        AccountRequests.Registration input,
        String email,
        String passwordHash
    ) {
        db.update(
            """
            INSERT INTO athletes(id,name,email,password_hash,bodyweight,standard)
            VALUES (?,?,?,?,?,?)
            """,
            id,
            input.name().trim(),
            email,
            passwordHash,
            input.bodyweight(),
            input.standard()
        );
    }

    public void update(UUID id, AccountRequests.Profile input) {
        db.update(
            """
            UPDATE athletes
            SET name=?,bodyweight=?,standard=?,ranks_enabled=COALESCE(?,ranks_enabled)
            WHERE id=?
            """,
            input.name().trim(),
            input.bodyweight(),
            input.standard(),
            input.ranksEnabled(),
            id
        );
    }
}
