package dev.changoff.wod;

import static dev.changoff.wod.WodModels.Draft;
import static dev.changoff.wod.WodModels.Exercise;
import static dev.changoff.wod.WodModels.Generate;
import static dev.changoff.wod.WodModels.Settings;
import static dev.changoff.wod.WodValidation.require;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WodGenerationService {

    private final WodCatalogRepository catalog;
    private final WodPreferenceService preferences;
    private final WodRepository workouts;
    private final WodExposureService exposure;
    private final WodGenerator generator;

    public WodGenerationService(
        WodCatalogRepository catalog,
        WodPreferenceService preferences,
        WodRepository workouts,
        WodExposureService exposure,
        WodGenerator generator
    ) {
        this.catalog = catalog;
        this.preferences = preferences;
        this.workouts = workouts;
        this.exposure = exposure;
        this.generator = generator;
    }

    public Draft generate(UUID user, Generate request) {
        Settings input = request.config();
        validate(input);
        Settings config = input.type().equals("AUTO")
            ? input.withType(generator.chooseType(workouts.recentTypes(user), input))
            : input;
        validate(config);
        return generator.generate(
            request,
            config,
            preferences.preferences(user),
            catalog.exercises(),
            request.exercises() == null ? exposure.exposure(user, config.scheduledOn()) : Map.of()
        );
    }

    void validate(Settings config) {
        catalog.validateIds(config.equipment(), "equipment");
        catalog.validateIds(config.bannedEquipment(), "equipment");
        catalog.validateIds(config.bannedExercises(), "wod_exercise");
        catalog.validateIds(config.bannedPatterns(), "movement_pattern");
        catalog.validateIds(config.bannedMuscles(), "muscle_group");
        if (!config.type().equals("AUTO")) {
            var template = catalog.template(config.type(), config.stimulus());
            require(
                config.count() >= ((Number) template.get("min_exercises")).intValue() &&
                    config.count() <= ((Number) template.get("max_exercises")).intValue(),
                "Exercise count is outside this template's range."
            );
        }
        require(
            !config.type().equals("EMOM") || config.duration() >= config.count(),
            "An EMOM needs at least one minute per movement."
        );
    }
}
