package dev.changoff.wod;

import static dev.changoff.wod.WodModels.Item;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WodExposureService {

    private final WodRepository workouts;
    private final WodSnapshots snapshots;

    public WodExposureService(WodRepository workouts, WodSnapshots snapshots) {
        this.workouts = workouts;
        this.snapshots = snapshots;
    }

    // Only a member's own completed results and nearby plans influence selection.
    public Map<String, Double> exposure(UUID user, LocalDate planned) {
        Map<String, Double> scores = new HashMap<>();
        var records = workouts.exposureRows(user, planned);
        for (var row : records) {
            Item item = snapshots.decode((String) row.get("snapshot_json"), Item.class);
            boolean done = row.get("status").equals("completed");
            LocalDate date = (
                (java.sql.Date) row.get(done ? "completed_on" : "scheduled_on")
            ).toLocalDate();
            double weight = done
                ? 1.0 /
                  (1 +
                      Math.max(
                          0,
                          java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now())
                      ) /
                          7.0)
                : .6;
            scores.merge("exercise:" + item.exercise().id(), weight, Double::sum);
            for (String muscle : item.exercise().muscles())
                scores.merge("muscle:" + muscle, weight, Double::sum);
        }
        return scores;
    }
}
