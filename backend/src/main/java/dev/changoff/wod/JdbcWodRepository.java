package dev.changoff.wod;

import static dev.changoff.wod.WodModels.Draft;
import static dev.changoff.wod.WodModels.Item;
import static dev.changoff.wod.WodModels.Result;
import static dev.changoff.wod.WodModels.Save;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWodRepository implements WodRepository {

    private final JdbcTemplate db;
    private final WodSnapshots snapshots;

    public JdbcWodRepository(JdbcTemplate db, WodSnapshots snapshots) {
        this.db = db;
        this.snapshots = snapshots;
    }

    public List<Map<String, Object>> exposureRows(UUID user, LocalDate planned) {
        return db.queryForList(
            """
            SELECT h.snapshot_json,u.status,u.completed_on,u.scheduled_on
            FROM history_exercise h
            JOIN user_wod u ON h.wod_id=u.wod_id
            WHERE u.user_id=?
              AND ((u.status='completed'
              AND u.completed_on>=?
              AND u.completed_on<=?)
              OR (u.status='planned'
              AND u.scheduled_on>=?
              AND u.scheduled_on<=?))
            """,
            user,
            LocalDate.now().minusDays(28),
            LocalDate.now(),
            planned.minusDays(7),
            planned.plusDays(7)
        );
    }

    public List<String> recentTypes(UUID user) {
        return db.queryForList(
            """
            SELECT w.type_id
            FROM wods w
            JOIN user_wod u ON u.wod_id=w.id
            WHERE u.user_id=?
              AND u.status<>'skipped'
            ORDER BY w.generated_at DESC
            LIMIT 4
            """,
            String.class,
            user
        );
    }

    public Optional<UUID> lockOwner(UUID id) {
        return db
            .queryForList("SELECT owner_id FROM wods WHERE id=? FOR UPDATE", UUID.class, id)
            .stream()
            .findFirst();
    }

    public int memberCount(UUID id, UUID user) {
        return db.queryForObject(
            "SELECT COUNT(*) FROM user_wod WHERE wod_id=? AND user_id=?",
            Integer.class,
            id,
            user
        );
    }

    public void insert(UUID id, UUID user, Save request, Draft draft) {
        db.update(
            """
            INSERT INTO wods(id,owner_id,title,type_id,stimulus_id,level_id,duration_minutes,rounds,intensity,assessed_difficulty,instructions,config_json)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            id,
            user,
            request.title().trim(),
            draft.config().type(),
            draft.config().stimulus(),
            draft.config().level(),
            draft.config().duration(),
            draft.rounds(),
            draft.config().intensity(),
            draft.difficulty(),
            draft.instructions(),
            snapshots.encode(draft.config())
        );
    }

    public void addMember(UUID id, UUID user, LocalDate date) {
        db.update(
            "INSERT INTO user_wod(wod_id,user_id,scheduled_on) VALUES(?,?,?)",
            id,
            user,
            date
        );
    }

    public boolean frozen(UUID id) {
        return Boolean.TRUE.equals(
            db.queryForObject("SELECT frozen FROM wods WHERE id=?", Boolean.class, id)
        );
    }

    public List<UUID> members(UUID id) {
        return db.queryForList("SELECT user_id FROM user_wod WHERE wod_id=?", UUID.class, id);
    }

    public int update(UUID id, Save request, Draft draft) {
        return db.update(
            """
            UPDATE wods
            SET title=?,type_id=?,stimulus_id=?,level_id=?,duration_minutes=?,rounds=?,intensity=?,assessed_difficulty=?,instructions=?,config_json=?,revision=revision+1
            WHERE id=?
              AND revision=?
            """,
            request.title().trim(),
            draft.config().type(),
            draft.config().stimulus(),
            draft.config().level(),
            draft.config().duration(),
            draft.rounds(),
            draft.config().intensity(),
            draft.difficulty(),
            draft.instructions(),
            snapshots.encode(draft.config()),
            id,
            request.revision()
        );
    }

    public void schedule(UUID id, UUID user, LocalDate date) {
        db.update(
            "UPDATE user_wod SET scheduled_on=? WHERE wod_id=? AND user_id=?",
            date,
            id,
            user
        );
    }

    public List<Map<String, Object>> detailRows(UUID user, UUID id) {
        return db.queryForList(
            """
            SELECT w.*,u.scheduled_on,u.status,u.completed_on,u.duration_seconds,u.rounds_completed,u.extra_reps,u.effort,u.notes
            FROM wods w
            JOIN user_wod u ON u.wod_id=w.id
            WHERE w.id=?
              AND u.user_id=?
            """,
            id,
            user
        );
    }

    public List<Map<String, Object>> participants(UUID id) {
        return db.queryForList(
            """
            SELECT a.id,a.name
            FROM athletes a
            JOIN user_wod u ON u.user_id=a.id
            WHERE u.wod_id=?
            ORDER BY u.joined_at,a.id
            """,
            id
        );
    }

    public void freeze(UUID id) {
        db.update("UPDATE wods SET frozen=TRUE WHERE id=?", id);
    }

    public void saveResult(UUID id, UUID user, Result r) {
        db.update(
            """
            UPDATE user_wod
            SET scheduled_on=?,status=?,completed_on=?,duration_seconds=?,rounds_completed=?,extra_reps=?,effort=?,notes=?
            WHERE wod_id=?
              AND user_id=?
            """,
            r.scheduledOn(),
            r.status(),
            r.completedOn(),
            r.status().equals("completed") ? r.durationSeconds() : null,
            r.status().equals("completed") ? r.roundsCompleted() : null,
            r.status().equals("completed") ? r.extraReps() : null,
            r.status().equals("completed") ? r.effort() : null,
            r.notes(),
            id,
            user
        );
    }

    public List<UUID> findUserByEmail(String email) {
        return db.queryForList(
            "SELECT id FROM athletes WHERE email=?",
            UUID.class,
            email.trim().toLowerCase(Locale.ROOT)
        );
    }

    public int participantCount(UUID id) {
        return db.queryForObject("SELECT COUNT(*) FROM user_wod WHERE wod_id=?", Integer.class, id);
    }

    public LocalDate scheduledDate(UUID id, UUID user) {
        return db.queryForObject(
            "SELECT scheduled_on FROM user_wod WHERE wod_id=? AND user_id=?",
            LocalDate.class,
            id,
            user
        );
    }

    public UUID owner(UUID id) {
        return db.queryForObject("SELECT owner_id FROM wods WHERE id=?", UUID.class, id);
    }

    public void removeMember(UUID id, UUID user) {
        db.update("DELETE FROM user_wod WHERE wod_id=? AND user_id=?", id, user);
    }

    public List<UUID> membersByJoinDate(UUID id) {
        return db.queryForList(
            "SELECT user_id FROM user_wod WHERE wod_id=? ORDER BY joined_at,user_id",
            UUID.class,
            id
        );
    }

    public void delete(UUID id) {
        db.update("DELETE FROM wods WHERE id=?", id);
    }

    public void transferOwnership(UUID id, UUID owner) {
        db.update("UPDATE wods SET owner_id=?,revision=revision+1 WHERE id=?", owner, id);
    }

    public List<Map<String, Object>> completed(UUID user, LocalDate start, LocalDate end) {
        return db.queryForList(
            """
            SELECT u.*,w.type_id
            FROM user_wod u
            JOIN wods w ON w.id=u.wod_id
            WHERE u.user_id=?
              AND u.status='completed'
              AND u.completed_on>=?
              AND u.completed_on<=?
            """,
            user,
            start,
            end
        );
    }

    public int completedInWeek(UUID user, LocalDate from) {
        return db.queryForObject(
            """
            SELECT COUNT(*)
            FROM user_wod
            WHERE user_id=?
              AND status='completed'
              AND completed_on>=?
              AND completed_on<?
            """,
            Integer.class,
            user,
            from,
            from.plusWeeks(1)
        );
    }

    public int upcoming(UUID user, LocalDate today) {
        return db.queryForObject(
            """
            SELECT COUNT(*)
            FROM user_wod
            WHERE user_id=?
              AND status='planned'
              AND scheduled_on>=?
            """,
            Integer.class,
            user,
            today
        );
    }

    public List<Map<String, Object>> history(UUID user) {
        return db.queryForList(
            """
            SELECT w.id,w.title,w.type_id,w.stimulus_id,w.level_id,w.duration_minutes,w.rounds,w.assessed_difficulty,w.generated_at,w.owner_id,w.revision,u.scheduled_on,u.status,u.completed_on,u.effort
            FROM wods w
            JOIN user_wod u ON u.wod_id=w.id
            WHERE u.user_id=?
            ORDER BY u.scheduled_on DESC,w.generated_at DESC
            """,
            user
        );
    }

    public List<Item> items(UUID id) {
        return db
            .queryForList(
                "SELECT snapshot_json FROM history_exercise WHERE wod_id=? ORDER BY position",
                String.class,
                id
            )
            .stream()
            .map(s -> snapshots.decode(s, Item.class))
            .toList();
    }

    public void replaceSnapshots(UUID id, Draft d) {
        db.update("DELETE FROM history_exercise WHERE wod_id=?", id);
        int position = 0;
        for (var item : d.items())
            db.update(
                "INSERT INTO history_exercise VALUES(?,?,?,?)",
                id,
                position++,
                item.exercise().id(),
                snapshots.encode(item)
            );
    }
}
