package dev.changoff.strength;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLiftRepository implements LiftRepository {

    private final JdbcTemplate db;

    public JdbcLiftRepository(JdbcTemplate db) {
        this.db = db;
    }

    public List<Map<String, Object>> history(UUID user) {
        return db.queryForList(
            """
            SELECT id,exercise,weight,reps,bodyweight,standard,score,performed_on
            FROM lifts
            WHERE user_id=?
            ORDER BY performed_on DESC,created_at DESC
            """,
            user
        );
    }

    public void insert(
        UUID id,
        UUID user,
        LiftInput input,
        double bodyweight,
        String standard,
        double score
    ) {
        db.update(
            """
            INSERT INTO lifts(id,user_id,exercise,weight,reps,bodyweight,standard,score,performed_on)
            VALUES(?,?,?,?,?,?,?,?,?)
            """,
            id,
            user,
            input.exercise(),
            input.weight(),
            input.reps(),
            bodyweight,
            standard,
            score,
            input.performedOn()
        );
    }

    public boolean delete(UUID id, UUID user) {
        return db.update("DELETE FROM lifts WHERE id=? AND user_id=?", id, user) > 0;
    }

    public double bestScore(UUID user, String exercise, String standard) {
        Double best = db.queryForObject(
            """
            SELECT MAX(score)
            FROM lifts
            WHERE user_id=?
              AND exercise=?
              AND standard=?
              AND reps<=12
            """,
            Double.class,
            user,
            exercise,
            standard
        );
        return best == null ? 0 : best;
    }
}
