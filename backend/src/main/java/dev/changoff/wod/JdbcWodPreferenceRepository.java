package dev.changoff.wod;

import static dev.changoff.wod.WodModels.Preferences;
import static dev.changoff.wod.WodModels.Restriction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWodPreferenceRepository implements WodPreferenceRepository {

    private final JdbcTemplate db;
    private final WodCatalogRepository catalog;

    public JdbcWodPreferenceRepository(JdbcTemplate db, WodCatalogRepository catalog) {
        this.db = db;
        this.catalog = catalog;
    }

    public Preferences preferences(UUID user) {
        int level = db.queryForObject(
            "SELECT wod_level_id FROM athletes WHERE id=?",
            Integer.class,
            user
        );
        boolean configured =
            db.queryForObject(
                "SELECT COUNT(*) FROM user_wod_preference WHERE user_id=?",
                Integer.class,
                user
            ) > 0;
        Set<String> equipment = configured
            ? new HashSet<>(
                  db.queryForList(
                      "SELECT equipment_id FROM user_equipment_preference WHERE user_id=?",
                      String.class,
                      user
                  )
              )
            : catalog.ids("equipment");
        List<Restriction> restrictions = db.query(
            "SELECT * FROM user_restriction WHERE user_id=? ORDER BY id",
            (r, n) -> {
                for (String kind : List.of("exercise", "pattern", "equipment", "muscle"))
                    if (r.getString(kind + "_id") != null) return new Restriction(
                        kind,
                        r.getString(kind + "_id"),
                        r.getString("reason"),
                        r.getBoolean("active")
                    );
                throw new IllegalStateException("Invalid stored restriction");
            },
            user
        );
        return new Preferences(level, equipment, restrictions);
    }

    public void replace(UUID user, Preferences p) {
        db.queryForObject("SELECT id FROM athletes WHERE id=? FOR UPDATE", UUID.class, user);
        db.update("UPDATE athletes SET wod_level_id=? WHERE id=?", p.level(), user);
        if (
            db.queryForObject(
                "SELECT COUNT(*) FROM user_wod_preference WHERE user_id=?",
                Integer.class,
                user
            ) == 0
        ) db.update("INSERT INTO user_wod_preference VALUES(?)", user);
        db.update("DELETE FROM user_equipment_preference WHERE user_id=?", user);
        for (String e : p.equipment())
            db.update("INSERT INTO user_equipment_preference VALUES(?,?)", user, e);
        db.update("DELETE FROM user_restriction WHERE user_id=?", user);
        for (var r : p.restrictions())
            db.update(
                "INSERT INTO user_restriction(id,user_id," +
                    r.kind() +
                    "_id,reason,active) VALUES(?,?,?,?,?)",
                UUID.randomUUID(),
                user,
                r.target(),
                r.reason(),
                r.active()
            );
    }
}
