package dev.changoff.cardio;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistence contract; the JDBC implementation owns SQL and row mapping. */
public interface CardioRepository {
    List<Map<String, Object>> history(UUID user);
    Map<String, Object> find(UUID id, UUID user);
    void insert(UUID id, UUID user, CardioInput input, String standard);
    boolean delete(UUID id, UUID user);
}
