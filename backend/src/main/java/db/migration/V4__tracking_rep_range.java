package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.util.ArrayList;

/** V1 used an unnamed check; PostgreSQL and H2 assign different names to it. */
public class V4__tracking_rep_range extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        var names = new ArrayList<String>();
        try (var query = connection.createStatement(); var rows = query.executeQuery("""
            SELECT tc.constraint_name, cc.check_clause
            FROM information_schema.table_constraints tc
            JOIN information_schema.check_constraints cc
              ON tc.constraint_catalog = cc.constraint_catalog
             AND tc.constraint_schema = cc.constraint_schema
             AND tc.constraint_name = cc.constraint_name
            WHERE LOWER(tc.table_name) = 'lifts' AND tc.constraint_type = 'CHECK'
              AND tc.table_schema = CURRENT_SCHEMA
            """)) {
            while (rows.next()) {
                String clause = rows.getString(2).toLowerCase(java.util.Locale.ROOT);
                // PostgreSQL also exposes NOT NULL as a synthetic information_schema check.
                // It must remain in place, and cannot be dropped by its synthetic name.
                if (clause.matches("(?s).*\\breps\\b.*") && !clause.contains("is not null"))
                    names.add(rows.getString(1));
            }
        }
        try (var statement = connection.createStatement()) {
            for (String name : names)
                statement.execute("ALTER TABLE lifts DROP CONSTRAINT \"" + name.replace("\"", "\"\"") + "\"");
            statement.execute("ALTER TABLE lifts ADD CONSTRAINT lifts_tracking_reps CHECK (reps BETWEEN 1 AND 1000)");
        }
    }
}
