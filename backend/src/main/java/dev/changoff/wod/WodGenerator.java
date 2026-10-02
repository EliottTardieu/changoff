package dev.changoff.wod;

import static dev.changoff.wod.WodModels.Draft;
import static dev.changoff.wod.WodModels.Exercise;
import static dev.changoff.wod.WodModels.Generate;
import static dev.changoff.wod.WodModels.Item;
import static dev.changoff.wod.WodModels.Preferences;
import static dev.changoff.wod.WodModels.Prescription;
import static dev.changoff.wod.WodModels.Settings;
import static dev.changoff.wod.WodValidation.conflict;
import static dev.changoff.wod.WodValidation.require;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Coordinates eligibility, explicit prescriptions, selection and assessment. */
@Service
public class WodGenerator {

    private final WodProgramming programming;
    private final WodSelection selection;

    public WodGenerator(WodProgramming programming, WodSelection selection) {
        this.programming = programming;
        this.selection = selection;
    }

    public String chooseType(List<String> recent, Settings config) {
        List<String> choices = new ArrayList<>(List.of("AMRAP", "EMOM", "FOR_TIME", "CHIPPER"));
        if (config.duration() < config.count()) choices.remove("EMOM");
        Collections.shuffle(choices);
        return choices
            .stream()
            .min(Comparator.comparingLong(t -> recent.stream().filter(t::equals).count()))
            .orElseThrow();
    }

    public Draft generate(
        Generate request,
        Settings config,
        Preferences preferences,
        List<Exercise> all,
        Map<String, Double> recent
    ) {
        List<Exercise> allowed = all
            .stream()
            .filter(
                exercise ->
                    MovementPolicy.permitted(exercise, preferences, config) &&
                    exercise.difficulty() <= Math.min(6, config.level() + 1)
            )
            .toList();
        if (allowed.size() < config.count()) throw conflict(
            "Only " +
                allowed.size() +
                " movements fit your equipment, level and bans. Reduce the exercise count or adjust your selections. No bans were ignored."
        );
        Map<String, Exercise> byId = allowed
            .stream()
            .collect(Collectors.toMap(Exercise::id, exercise -> exercise));

        List<Prescription> prescriptions =
            request.exercises() != null
                ? validatePrescriptions(request, config, byId)
                : selection.select(config, allowed, byId, recent);
        return programming.assess(config, withAlternatives(prescriptions, allowed, byId, config));
    }

    private List<Prescription> validatePrescriptions(
        Generate request,
        Settings config,
        Map<String, Exercise> byId
    ) {
        List<Prescription> prescriptions = new ArrayList<>();

        require(
            request.exercises().size() == config.count(),
            "The exercise count must match the workout."
        );
        Set<String> unique = new HashSet<>();
        for (var set : request.exercises()) {
            require(
                byId.containsKey(set.exerciseId()),
                "A selected movement is excluded by your level, equipment or bans."
            );
            require(unique.add(set.exerciseId()), "Use each movement only once.");
            require(Double.isFinite(set.weight()), "Invalid load.");
            Exercise exercise = byId.get(set.exerciseId());
            require(
                exercise.category().equals("weightlifting") || set.weight() == 0,
                "External load is only supported on weighted movements."
            );
            if (config.type().equals("EMOM")) require(
                set.quantity() * exercise.secondsPerUnit() <= 45,
                "This EMOM station exceeds 45 estimated work seconds; lower the quantity to leave rest."
            );
            prescriptions.add(set);
        }

        return prescriptions;
    }

    private List<Item> withAlternatives(
        List<Prescription> prescriptions,
        List<Exercise> allowed,
        Map<String, Exercise> byId,
        Settings config
    ) {
        Set<String> selected = prescriptions
            .stream()
            .map(Prescription::exerciseId)
            .collect(Collectors.toSet());
        List<Item> items = new ArrayList<>();
        for (var prescription : prescriptions) {
            Exercise exercise = byId.get(prescription.exerciseId());
            List<Prescription> alternatives = allowed
                .stream()
                .filter(
                    alternative ->
                        !selected.contains(alternative.id()) &&
                        alternative.difficulty() <= exercise.difficulty() &&
                        (alternative.family().equals(exercise.family()) ||
                            !Collections.disjoint(alternative.patterns(), exercise.patterns()))
                )
                .sorted(
                    Comparator.comparingInt((Exercise alternative) ->
                        alternative.family().equals(exercise.family()) ? 0 : 1
                    ).thenComparingInt(Exercise::difficulty)
                )
                .limit(5)
                .map(alternative -> programming.dose(alternative, config))
                .toList();
            items.add(
                new Item(exercise, prescription.quantity(), prescription.weight(), alternatives)
            );
        }

        return items;
    }
}
