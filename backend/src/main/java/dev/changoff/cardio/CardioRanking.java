package dev.changoff.cardio;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Exact published distance/time thresholds; no interpolation for unsupported activities. */
@Service
public class CardioRanking {

    public record Benchmark(
        String activity,
        int distanceMeters,
        String source,
        String reference,
        List<String> labels,
        List<Integer> male,
        List<Integer> female
    ) {}

    private final List<Benchmark> benchmarks;

    public CardioRanking(ObjectMapper mapper) throws IOException {
        try (var stream = getClass().getResourceAsStream("/cardio-benchmarks.json")) {
            benchmarks = mapper.readValue(stream, new TypeReference<>() {});
        }
    }

    public List<Benchmark> benchmarks() {
        return benchmarks;
    }

    Benchmark benchmark(String activity, Integer distance) {
        return benchmarks
            .stream()
            .filter(
                b -> b.activity().equals(activity) && Objects.equals(b.distanceMeters(), distance)
            )
            .findFirst()
            .orElse(null);
    }

    public Map<String, Object> ranked(Map<String, Object> row) {
        var result = new LinkedHashMap<>(row);
        String activity = (String) row.get("activity"),
            standard = (String) row.get("standard");
        Integer distance =
            row.get("distance_meters") == null
                ? null
                : ((Number) row.get("distance_meters")).intValue();
        double duration = ((Number) row.get("duration_seconds")).doubleValue();
        var benchmark = benchmark(activity, distance);
        result.put("rank", null);
        result.put("source", benchmark == null ? null : benchmark.source());
        result.put("reference", benchmark == null ? null : benchmark.reference());
        if (benchmark != null) {
            var times = standard.equals("female") ? benchmark.female() : benchmark.male();
            String rank = "Below beginner benchmark";
            for (int i = 0; i < times.size(); i++) if (duration <= times.get(i)) rank = benchmark
                .labels()
                .get(i);
            result.put("rank", rank);
        }
        result.put(
            "rate",
            activity.equals("rope")
                ? (((Number) row.get("reps")).doubleValue() * 60) / duration
                : (distance * 3.6) / duration
        );
        return result;
    }
}
