package dev.changoff.wod;

import static dev.changoff.wod.WodModels.Exercise;
import static dev.changoff.wod.WodModels.Prescription;
import static dev.changoff.wod.WodModels.Settings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Chooses variety only among movements that already passed all hard restrictions. */
@Service
public class WodSelection {

    private final WodProgramming programming;

    public WodSelection(WodProgramming programming) {
        this.programming = programming;
    }

    public List<Prescription> select(
        Settings config,
        List<Exercise> allowed,
        Map<String, Exercise> byId,
        Map<String, Double> recent
    ) {
        List<Prescription> prescriptions = new ArrayList<>();

        Set<String> picked = new HashSet<>(),
            families = new HashSet<>(),
            muscles = new HashSet<>(),
            patterns = new HashSet<>();
        for (int index = 0; index < config.count(); index++) {
            Map<String, Double> noise = new HashMap<>();
            allowed.forEach(exercise ->
                noise.put(exercise.id(), ThreadLocalRandom.current().nextDouble(.8))
            );
            Exercise best = allowed
                .stream()
                .filter(exercise -> !picked.contains(exercise.id()))
                .min(
                    Comparator.comparingDouble(exercise -> {
                        double history = exercise
                            .muscles()
                            .stream()
                            .mapToDouble(muscle -> recent.getOrDefault("muscle:" + muscle, 0.0))
                            .average()
                            .orElse(0);
                        double repeated =
                            exercise.muscles().stream().filter(muscles::contains).count() * .3;
                        double stimulus = config.stimulus().equals("engine")
                            ? exercise.category().equals("conditioning")
                                ? -1
                                : 0
                            : config.stimulus().equals("strength")
                              ? exercise.category().equals("weightlifting")
                                  ? -1
                                  : 0
                              : 0;
                        return (
                            history +
                            recent.getOrDefault("exercise:" + exercise.id(), 0.0) * 2 +
                            (families.contains(exercise.family()) ? 6 : 0) +
                            (patterns.containsAll(exercise.patterns()) ? 1.5 : 0) +
                            repeated +
                            stimulus +
                            noise.get(exercise.id())
                        );
                    })
                )
                .orElseThrow();
            prescriptions.add(programming.dose(best, config));
            picked.add(best.id());
            families.add(best.family());
            muscles.addAll(best.muscles());
            patterns.addAll(best.patterns());
        }
        // Chippers are single-pass workouts, scale their estimated work toward the chosen cap.
        if (config.type().equals("CHIPPER")) {
            double estimated = prescriptions
                .stream()
                .mapToDouble(
                    prescription ->
                        prescription.quantity() *
                        byId.get(prescription.exerciseId()).secondsPerUnit()
                )
                .sum();
            double scale = Math.max(.5, Math.min(5, (config.duration() * 60 * .75) / estimated));
            prescriptions = prescriptions
                .stream()
                .map(prescription ->
                    new Prescription(
                        prescription.exerciseId(),
                        Math.max(1, (int) Math.round(prescription.quantity() * scale)),
                        prescription.weight()
                    )
                )
                .collect(Collectors.toCollection(ArrayList::new));
        }

        return prescriptions;
    }
}
