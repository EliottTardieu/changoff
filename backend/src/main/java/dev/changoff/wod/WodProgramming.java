package dev.changoff.wod;

import static dev.changoff.wod.WodModels.Dose;
import static dev.changoff.wod.WodModels.Draft;
import static dev.changoff.wod.WodModels.Exercise;
import static dev.changoff.wod.WodModels.Item;
import static dev.changoff.wod.WodModels.Prescription;
import static dev.changoff.wod.WodModels.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Existing load, volume and difficulty formulas, kept independent of persistence. */
@Service
public class WodProgramming {

    Prescription dose(Exercise exercise, Settings config) {
        Dose base = exercise.levels().get(config.level());
        double factor = .65 + .175 * config.intensity();
        int amount = Math.max(1, (int) Math.round(base.quantity() * factor));
        if (config.type().equals("CHIPPER")) amount *= 3;
        if (config.type().equals("EMOM")) amount = Math.max(
            1,
            Math.min(amount, (int) Math.floor(40 / exercise.secondsPerUnit()))
        );
        double weight = Math.round(base.weight() * factor * 2) / 2.0;
        return new Prescription(exercise.id(), amount, weight);
    }

    public Draft assess(Settings config, List<Item> items) {
        int rounds = config.type().equals("CHIPPER")
            ? 1
            : config.type().equals("EMOM")
              ? (int) Math.ceil((double) config.duration() / config.count())
              : config.type().equals("AMRAP")
                ? 0
                : config.rounds();
        double perRound = items
            .stream()
            .mapToDouble(item -> item.quantity() * item.exercise().secondsPerUnit())
            .sum();
        double averageSkill = items
            .stream()
            .mapToInt(item -> item.exercise().difficulty())
            .average()
            .orElse(1);
        double doseRatio = items
            .stream()
            .mapToDouble(item -> {
                Prescription base = dose(item.exercise(), config);
                return (
                    ((double) item.quantity() / base.quantity() +
                        (base.weight() > 0 ? item.weight() / base.weight() : 1)) /
                    2
                );
            })
            .average()
            .orElse(1);
        double difficulty =
            Math.round(
                Math.max(
                    1,
                    Math.min(
                        10,
                        averageSkill +
                            .6 * config.level() +
                            .7 * (config.intensity() - 3) +
                            Math.max(-1, Math.min(2, doseRatio - 1))
                    )
                ) * 10
            ) / 10.0;
        String instructions = switch (config.type()) {
            case "AMRAP" -> "For " +
                config.duration() +
                " minutes, repeat the circuit for as many quality rounds and reps as possible. Rest as needed. Record completed rounds plus extra reps.";
            case "EMOM" -> "For " +
                config.duration() +
                " minutes, start one station at the beginning of each minute, cycling through the listed order. Rest for the remainder of the minute. The final cycle may be partial.";
            case "CHIPPER" -> "Complete each movement in order once. Finish all its prescribed work before moving on. Time cap: " +
                config.duration() +
                " minutes.";
            default -> "Complete " +
                rounds +
                " rounds in order for time. Time cap: " +
                config.duration() +
                " minutes. Record your time or unfinished work in notes.";
        };
        List<String> explanations = new ArrayList<>(
            List.of(
                "All equipment requirements and active bans were checked.",
                "Selection favors less-used muscles and movements from your last 28 days of completed WODs and plans within 7 days of this date.",
                "Difficulty is a programming estimate (1–10), not a medical or performance assessment."
            )
        );
        Set<String> covered = items
            .stream()
            .flatMap(item -> item.exercise().muscles().stream())
            .collect(Collectors.toSet());
        explanations.add(
            "Coverage in this WOD: " +
                String.join(", ", new TreeSet<>(covered)) +
                ". Balance is an aim across sessions, not a guarantee for a single WOD."
        );
        if (
            config.type().equals("FOR_TIME") && perRound * rounds > config.duration() * 60
        ) explanations.add(
            "Estimated moving time exceeds the cap. Consider fewer rounds or lower quantities; it is fine to stop at the cap."
        );
        return new Draft(config, items, rounds, difficulty, instructions, explanations);
    }
}
