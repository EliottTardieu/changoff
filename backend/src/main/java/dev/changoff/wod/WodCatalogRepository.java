package dev.changoff.wod;

import static dev.changoff.wod.WodModels.Exercise;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Persistence contract; the JDBC implementation owns SQL and row mapping. */
public interface WodCatalogRepository {
    List<Exercise> exercises();
    Map<String, Object> all();
    Set<String> ids(String table);
    void validateIds(Set<String> given, String table);
    Map<String, Object> template(String type, String stimulus);
}
