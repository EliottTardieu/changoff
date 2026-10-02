package dev.changoff.wod;

import static dev.changoff.wod.WodModels.Dose;
import static dev.changoff.wod.WodModels.Exercise;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWodCatalogRepository implements WodCatalogRepository {

    private final JdbcTemplate db;

    public JdbcWodCatalogRepository(JdbcTemplate db) {
        this.db = db;
    }

    List<String> links(String table, String column, String id) {
        return db.queryForList(
            "SELECT " + column + " FROM " + table + " WHERE exercise_id=? ORDER BY " + column,
            String.class,
            id
        );
    }

    public List<Exercise> exercises() {
        return db.query("SELECT * FROM wod_exercise ORDER BY name", (rs, row) -> {
            String id = rs.getString("id");
            Map<Integer, Dose> doses = new LinkedHashMap<>();
            db.query(
                "SELECT * FROM exercise_level_config WHERE exercise_id=? ORDER BY level_id",
                (org.springframework.jdbc.core.RowCallbackHandler) r ->
                    doses.put(
                        r.getInt("level_id"),
                        new Dose(r.getInt("suggested_reps"), r.getDouble("suggested_weight"))
                    ),
                id
            );
            return new Exercise(
                id,
                rs.getString("name"),
                rs.getString("family_id"),
                rs.getString("category_id"),
                rs.getString("movement_type_id"),
                rs.getInt("difficulty"),
                rs.getString("unit"),
                rs.getDouble("seconds_per_unit"),
                rs.getString("note"),
                links("exercise_equipment", "equipment_id", id),
                links("exercise_pattern", "pattern_id", id),
                links("exercise_muscle", "muscle_id", id),
                doses
            );
        });
    }

    public Map<String, Object> all() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exercises", exercises());
        for (var entry : Map.of(
            "equipment",
            "equipment",
            "patterns",
            "movement_pattern",
            "muscles",
            "muscle_group",
            "levels",
            "wod_level",
            "types",
            "wod_type",
            "stimuli",
            "wod_stimulus",
            "families",
            "exercise_family",
            "categories",
            "exercise_category",
            "movementTypes",
            "movement_type"
        ).entrySet())
            result.put(
                entry.getKey(),
                db.queryForList("SELECT * FROM " + entry.getValue() + " ORDER BY id")
            );
        result.put("templates", db.queryForList("SELECT * FROM wod_template ORDER BY id"));
        return result;
    }

    // Table identifiers come from application code, never from request values.
    public Set<String> ids(String table) {
        if (
            !Set.of(
                "equipment",
                "wod_exercise",
                "movement_pattern",
                "muscle_group",
                "wod_type"
            ).contains(table)
        ) throw new IllegalArgumentException("Unknown catalog table");
        return new HashSet<>(db.queryForList("SELECT id FROM " + table, String.class));
    }

    public void validateIds(Set<String> given, String table) {
        WodValidation.require(
            ids(table).containsAll(given),
            "Unknown selection for " + table + "."
        );
    }

    public Map<String, Object> template(String type, String stimulus) {
        return db.queryForMap(
            "SELECT * FROM wod_template WHERE type_id=? AND stimulus_id=?",
            type,
            stimulus
        );
    }
}
