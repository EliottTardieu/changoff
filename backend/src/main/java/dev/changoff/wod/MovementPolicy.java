package dev.changoff.wod;

import static dev.changoff.wod.WodModels.Exercise;
import static dev.changoff.wod.WodModels.Preferences;
import static dev.changoff.wod.WodModels.Settings;

import java.util.Collections;

/** Hard restrictions are shared by generation and participant compatibility checks. */
final class MovementPolicy {

    private MovementPolicy() {}

    static boolean permitted(Exercise exercise, Preferences preferences, Settings config) {
        if (!preferences.equipment().containsAll(exercise.equipment())) return false;
        if (
            config != null &&
            (!config.equipment().containsAll(exercise.equipment()) ||
                config.bannedExercises().contains(exercise.id()) ||
                !Collections.disjoint(exercise.patterns(), config.bannedPatterns()) ||
                !Collections.disjoint(exercise.muscles(), config.bannedMuscles()) ||
                !Collections.disjoint(exercise.equipment(), config.bannedEquipment()))
        ) return false;
        for (var restriction : preferences.restrictions())
            if (
                restriction.active() &&
                switch (restriction.kind()) {
                    case "exercise" -> exercise.id().equals(restriction.target());
                    case "muscle" -> exercise.muscles().contains(restriction.target());
                    case "pattern" -> exercise.patterns().contains(restriction.target());
                    default -> exercise.equipment().contains(restriction.target());
                }
            ) return false;
        return true;
    }
}
