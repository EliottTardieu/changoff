package dev.changoff.wod;

import static dev.changoff.wod.WodModels.Item;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WodStatisticsService {

    private final WodRepository workouts;
    private final WodCatalogRepository catalog;

    public WodStatisticsService(WodRepository workouts, WodCatalogRepository catalog) {
        this.workouts = workouts;
        this.catalog = catalog;
    }

    public Map<String, Object> statistics(UUID user) {
        LocalDate today = LocalDate.now(),
            start = today.minusDays(27),
            monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var completed = workouts.completed(user, start, today);
        Map<String, Integer> muscleCounts = new TreeMap<>();
        catalog.ids("muscle_group").forEach(m -> muscleCounts.put(m, 0));
        Map<String, Integer> types = new TreeMap<>();
        catalog.ids("wod_type").forEach(t -> types.put(t, 0));
        Map<String, Integer> movements = new HashMap<>();
        List<Map<String, Object>> weeks = new ArrayList<>();
        for (int n = 7; n >= 0; n--) {
            LocalDate from = monday.minusWeeks(n);
            int count = workouts.completedInWeek(user, from);
            weeks.add(Map.of("week", from, "count", count));
        }
        double effort = 0;
        int effortCount = 0,
            seconds = 0;
        Set<LocalDate> days = new HashSet<>();
        for (var row : completed) {
            UUID id = (UUID) row.get("wod_id");
            Set<String> hit = new HashSet<>();
            for (Item i : workouts.items(id)) {
                hit.addAll(i.exercise().muscles());
                movements.merge(i.exercise().id(), 1, Integer::sum);
            }
            hit.forEach(m -> muscleCounts.merge(m, 1, Integer::sum));
            types.merge((String) row.get("type_id"), 1, Integer::sum);
            days.add(((java.sql.Date) row.get("completed_on")).toLocalDate());
            if (row.get("effort") != null) {
                effort += ((Number) row.get("effort")).doubleValue();
                effortCount++;
            }
            if (row.get("duration_seconds") != null) seconds += (
                (Number) row.get("duration_seconds")
            ).intValue();
        }
        int max = muscleCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0),
            min = muscleCounts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("completed", completed.size());
        result.put("trainingDays", days.size());
        result.put("loggedMinutes", Math.round(seconds / 60.0));
        result.put(
            "averageEffort",
            effortCount == 0 ? null : Math.round((effort / effortCount) * 10) / 10.0
        );
        result.put("upcoming", workouts.upcoming(user, today));
        result.put("weekly", weeks);
        result.put("muscles", muscleCounts);
        result.put("types", types);
        result.put("uniqueMovements", movements.size());
        result.put(
            "mostHit",
            max == 0
                ? List.of()
                : muscleCounts
                      .entrySet()
                      .stream()
                      .filter(e -> e.getValue() == max)
                      .map(Map.Entry::getKey)
                      .toList()
        );
        result.put(
            "leastHit",
            completed.isEmpty()
                ? List.of()
                : muscleCounts
                      .entrySet()
                      .stream()
                      .filter(e -> e.getValue() == min)
                      .map(Map.Entry::getKey)
                      .toList()
        );
        result.put("windowStart", start);
        result.put("windowEnd", today);
        return result;
    }
}
