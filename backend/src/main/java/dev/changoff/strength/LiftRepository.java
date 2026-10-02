package dev.changoff.strength;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistence contract; the JDBC implementation owns SQL and row mapping. */
public interface LiftRepository {
    List<Map<String, Object>> history(UUID user);
    void insert(
        UUID id,
        UUID user,
        LiftInput input,
        double bodyweight,
        String standard,
        double score
    );
    boolean delete(UUID id, UUID user);
    double bestScore(UUID user, String exercise, String standard);
}
