package dev.changoff;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.changoff.strength.Ranking;
import org.junit.jupiter.api.Test;

class RankingTest {

    Ranking r;

    RankingTest() throws Exception {
        r = new Ranking(new ObjectMapper());
    }

    @Test
    void allRanksAreStrictlyIncreasingAndDisplayedSetsQualify() {
        assertEquals(12, r.exercises().size());
        for (var e : r.exercises())
            for (String sex : new String[] { "male", "female" }) {
                double previous = 0;
                for (int level = 1; level <= 24; level++) {
                    double t = r.threshold(e, sex, level);
                    assertTrue(t > previous);
                    assertEquals(level, r.level(e, sex, t));
                    previous = t;
                }
                for (int reps = 1; reps <= 12; reps++) for (var t : r.targets(e, sex, 75, reps))
                    assertTrue(r.score(e, t.weight(), reps, 75) + 1e-9 >= t.ratio());
            }
    }

    @Test
    void epleyUsesActualSingleRepAndIncludesBodyweight() {
        assertEquals(100, Ranking.estimate(100, 1));
        assertEquals(120, Ranking.estimate(100, 6));
        assertEquals(1, r.score(r.exercise("pull-ups"), 0, 1, 75));
        assertEquals(1.2, r.score(r.exercise("pull-ups"), 15, 1, 75), 1e-9);
    }

    @Test
    void sourceAnchorsAndRankBoundaries() {
        var bench = r.exercise("bench-press");
        assertEquals(.5, r.threshold(bench, "male", 1));
        assertEquals(1, r.threshold(bench, "male", 4));
        assertEquals(1.25, r.threshold(bench, "male", 10));
        assertEquals(2, r.threshold(bench, "male", 21));
        assertEquals(2.3, r.threshold(bench, "male", 24));
        assertEquals(0, r.level(bench, "male", .49));
        assertEquals(24, r.level(bench, "male", 99));
        assertEquals("Bronze 1", Ranking.rank(1));
        assertEquals("Chang 3", Ranking.rank(24));
    }
}
