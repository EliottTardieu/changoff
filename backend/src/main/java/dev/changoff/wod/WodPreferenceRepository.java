package dev.changoff.wod;

import static dev.changoff.wod.WodModels.Preferences;

import java.util.UUID;

/** Persistence contract; the JDBC implementation owns SQL and row mapping. */
public interface WodPreferenceRepository {
    Preferences preferences(UUID user);
    void replace(UUID user, Preferences p);
}
