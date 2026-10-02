package dev.changoff.cardio;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCardioRepository implements CardioRepository {

    private final JdbcTemplate db;

    public JdbcCardioRepository(JdbcTemplate db) {
        this.db = db;
    }

    public List<Map<String, Object>> history(UUID user) {
        return db.queryForList(
            """
            SELECT *
            FROM cardio_sessions
            WHERE user_id=?
            ORDER BY performed_on DESC,created_at DESC
            """,
            user
        );
    }

    public Map<String, Object> find(UUID id, UUID user) {
        return db.queryForMap("SELECT * FROM cardio_sessions WHERE id=? AND user_id=?", id, user);
    }

    public void insert(UUID id, UUID user, CardioInput input, String standard) {
        db.update(
            """
            INSERT INTO cardio_sessions(id,user_id,activity,distance_meters,reps,duration_seconds,standard,performed_on,notes)
            VALUES(?,?,?,?,?,?,?,?,?)
            """,
            id,
            user,
            input.activity(),
            input.distanceMeters(),
            input.reps(),
            input.durationSeconds(),
            standard,
            input.performedOn(),
            input.notes() == null ? "" : input.notes().trim()
        );
    }

    public boolean delete(UUID id, UUID user) {
        return db.update("DELETE FROM cardio_sessions WHERE id=? AND user_id=?", id, user) > 0;
    }
}
