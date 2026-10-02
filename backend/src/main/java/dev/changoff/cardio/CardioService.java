package dev.changoff.cardio;

import dev.changoff.account.AccountService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CardioService {

    private final CardioRepository sessions;
    private final CardioRanking ranking;
    private final AccountService accounts;

    public CardioService(
        CardioRepository sessions,
        CardioRanking ranking,
        AccountService accounts
    ) {
        this.sessions = sessions;
        this.ranking = ranking;
        this.accounts = accounts;
    }

    public List<CardioRanking.Benchmark> benchmarks() {
        return ranking.benchmarks();
    }

    public List<Map<String, Object>> history(UUID user) {
        return sessions.history(user).stream().map(ranking::ranked).toList();
    }

    @Transactional
    public Map<String, Object> save(CardioInput in, UUID user) {
        if (!Double.isFinite(in.durationSeconds())) throw new IllegalArgumentException(
            "Enter a valid duration."
        );
        if (in.activity().equals("rope")) {
            if (
                in.reps() == null || in.distanceMeters() != null
            ) throw new IllegalArgumentException(
                "Jump rope requires reps and duration, without distance."
            );
        } else {
            if (
                in.distanceMeters() == null || in.reps() != null
            ) throw new IllegalArgumentException(
                "Running and cycling require distance and duration, without reps."
            );
            if (
                in.activity().equals("running") &&
                !Set.of(400, 1000, 5000, 10000, 20000).contains(in.distanceMeters())
            ) throw new IllegalArgumentException("Choose a supported running distance.");
        }
        UUID id = UUID.randomUUID();
        String standard = accounts.profile(user).standard();
        sessions.insert(id, user, in, standard);
        return ranking.ranked(sessions.find(id, user));
    }

    public void delete(UUID id, UUID user) {
        if (!sessions.delete(id, user)) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Cardio entry not found."
        );
    }
}
