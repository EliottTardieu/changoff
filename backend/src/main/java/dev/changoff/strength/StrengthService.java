package dev.changoff.strength;

import dev.changoff.account.AccountService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StrengthService {

    private final LiftRepository lifts;
    private final Ranking ranking;
    private final AccountService accounts;

    public StrengthService(LiftRepository lifts, Ranking ranking, AccountService accounts) {
        this.lifts = lifts;
        this.ranking = ranking;
        this.accounts = accounts;
    }

    public List<Ranking.Exercise> exercises() {
        return ranking.exercises();
    }

    public List<Ranking.Target> targets(String id, int reps, UUID user) {
        if (reps < 1 || reps > 12) throw new IllegalArgumentException(
            "Reps must be between 1 and 12."
        );
        var p = accounts.profile(user);
        return ranking.targets(ranking.exercise(id), p.standard(), p.bodyweight(), reps);
    }

    public List<Map<String, Object>> lifts(UUID user) {
        return lifts.history(user);
    }

    public Map<String, Object> log(LiftInput in, UUID user) {
        UUID uid = user;
        var p = accounts.profile(uid);
        var e = ranking.exercise(in.exercise());
        if (
            !Double.isFinite(in.weight()) || (!e.bodyweightMovement() && in.weight() <= 0)
        ) throw new IllegalArgumentException(
            "Enter a positive weight; bodyweight movements may use 0."
        );
        double bw = p.bodyweight();
        String standard = p.standard();
        double score = in.reps() <= 12 ? ranking.score(e, in.weight(), in.reps(), bw) : 0;
        UUID id = UUID.randomUUID();
        lifts.insert(id, uid, in, bw, standard, score);
        return Map.of("id", id, "rank", Ranking.rank(ranking.level(e, standard, score)));
    }

    public void delete(UUID id, UUID user) {
        if (!lifts.delete(id, user)) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Set not found."
        );
    }

    public List<Map<String, Object>> dashboard(UUID user) {
        UUID id = user;
        var p = accounts.profile(id);
        String standard = p.standard();
        return ranking
            .exercises()
            .stream()
            .map(e -> {
                double score = lifts.bestScore(id, e.id(), standard);
                int level = ranking.level(e, standard, score);
                double low = level == 0 ? 0 : ranking.threshold(e, standard, level);
                double high = level == 24 ? low : ranking.threshold(e, standard, level + 1);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("exercise", e);
                row.put("level", level);
                row.put("rank", Ranking.rank(level));
                row.put("score", score);
                row.put(
                    "progress",
                    level == 24
                        ? 100
                        : Math.max(0, Math.min(100, (100 * (score - low)) / (high - low)))
                );
                row.put("nextRank", level == 24 ? "Maximum rank" : Ranking.rank(level + 1));
                return row;
            })
            .toList();
    }
}
