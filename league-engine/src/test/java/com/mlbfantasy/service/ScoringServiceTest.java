package com.mlbfantasy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbfantasy.dto.ScoreBreakdown;
import com.mlbfantasy.model.DailyPerformance;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Exercises the pure scoring math directly (no DB, no mocking framework) so the test
 * is deterministic across JDKs
 */
class ScoringServiceTest {

    private final ScoringService scoringService = new ScoringService(null, null, null, null);
    private final UUID userId = UUID.randomUUID();

    @Test
    void appliesPointValuesAcrossBoxScores() {
        Map<String, Double> rules = Map.of(
                "home_runs", 4.0,
                "rbi", 1.0,
                "strikeouts_batting", -1.0);

        // Two box scores (e.g. a doubleheader): 2 HR, 3 RBI, 4 batter Ks total.
        List<DailyPerformance> performances = List.of(
                performance(1, 2, 3),
                performance(1, 1, 1));

        ScoreBreakdown breakdown = scoringService.computeBreakdown(userId, performances, rules);

        // (2 HR * 4) + (3 RBI * 1) + (4 K * -1) = 8 + 3 - 4 = 7
        assertThat(breakdown.totalPoints()).isEqualByComparingTo(new BigDecimal("7.00"));
        assertThat(breakdown.categoryPoints())
                .containsEntry("home_runs", new BigDecimal("8.00"))
                .containsEntry("rbi", new BigDecimal("3.00"))
                .containsEntry("strikeouts_batting", new BigDecimal("-4.00"));
    }

    @Test
    void ignoresUnknownStatKeys() {
        Map<String, Double> rules = Map.of("not_a_real_stat", 10.0, "home_runs", 4.0);
        ScoreBreakdown breakdown = scoringService.computeBreakdown(
                userId, List.of(performance(1, 0, 0)), rules);

        assertThat(breakdown.categoryPoints()).containsOnlyKeys("home_runs");
        assertThat(breakdown.totalPoints()).isEqualByComparingTo(new BigDecimal("4.00"));
    }

    @Test
    void returnsZeroWithNoPerformances() {
        ScoreBreakdown breakdown = scoringService.computeBreakdown(
                userId, List.of(), Map.of("home_runs", 4.0));

        assertThat(breakdown.totalPoints()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(breakdown.categoryPoints()).containsEntry("home_runs", new BigDecimal("0.00"));
    }

    private DailyPerformance performance(int homeRuns, int rbi, int strikeoutsBatting) {
        DailyPerformance p = newInstance(DailyPerformance.class);
        setField(p, "homeRuns", homeRuns);
        setField(p, "rbi", rbi);
        setField(p, "strikeoutsBatting", strikeoutsBatting);
        return p;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
