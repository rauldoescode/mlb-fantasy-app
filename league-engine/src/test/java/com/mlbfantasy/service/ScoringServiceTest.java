package com.mlbfantasy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbfantasy.dto.PlayerGamePerformance;
import com.mlbfantasy.dto.PlayerWeekScore;
import com.mlbfantasy.dto.ScoreBreakdown;
import com.mlbfantasy.dto.WeekScoreResult;
import com.mlbfantasy.model.DailyPerformance;
import com.mlbfantasy.model.LineupEligibility;
import com.mlbfantasy.model.MatchupPlayerScore;
import com.mlbfantasy.model.PerformanceLock;
import com.mlbfantasy.model.RosterSlot;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Exercises pure scoring math directly (no DB) so tests stay deterministic.
 */
class ScoringServiceTest {

    private final ScoringService scoringService =
            new ScoringService(null, null, null, null, null, null, null);
    private final UUID userId = UUID.randomUUID();
    private final UUID leagueId = UUID.randomUUID();
    private final Map<String, Double> rules = Map.of(
            "home_runs", 4.0,
            "rbi", 1.0,
            "strikeouts_batting", -1.0);

    @Test
    void appliesPointValuesAcrossBoxScores() {
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
        Map<String, Double> rulesWithUnknown = Map.of("not_a_real_stat", 10.0, "home_runs", 4.0);
        ScoreBreakdown breakdown = scoringService.computeBreakdown(
                userId, List.of(performance(1, 0, 0)), rulesWithUnknown);

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

    @Test
    void weekScoreUsesBestEligibleGameNotSumOfAllGames() {
        Integer playerId = 101;
        RosterSlot slot = new RosterSlot(leagueId, userId, playerId, "BENCH", true);
        LocalDate monday = LocalDate.of(2026, 3, 23);
        LocalDate wednesday = LocalDate.of(2026, 3, 25);

        // Monday: 1 HR = 4 pts; Wednesday: 2 HR = 8 pts → best = Wednesday.
        List<DailyPerformance> performances = List.of(
                performance(playerId, monday, 1001, 1, 0, 0),
                performance(playerId, wednesday, 1002, 2, 0, 0));

        WeekScoreResult result = scoringService.computeWeekScore(
                userId, List.of(slot), List.of(), List.of(), performances, rules);

        assertThat(result.breakdown().totalPoints()).isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(result.playerScores()).hasSize(1);
        assertThat(result.playerScores().get(0).scoringGamePk()).isEqualTo(1002);
        assertThat(result.playerScores().get(0).points()).isEqualByComparingTo(new BigDecimal("8.00"));
    }

    @Test
    void midWeekBenchDoesNotEraseLockedEligibilityGame() {
        Integer playerId = 202;
        // Currently benched, but Monday eligibility was started.
        RosterSlot slot = new RosterSlot(leagueId, userId, playerId, "BENCH", false);
        LocalDate monday = LocalDate.of(2026, 3, 23);
        LocalDate wednesday = LocalDate.of(2026, 3, 25);

        LineupEligibility mondayStarted =
                new LineupEligibility(leagueId, userId, 1, playerId, monday, true);
        LineupEligibility wednesdayBenched =
                new LineupEligibility(leagueId, userId, 1, playerId, wednesday, false);

        List<DailyPerformance> performances = List.of(
                performance(playerId, monday, 2001, 2, 0, 0),      // 8 pts, eligible
                performance(playerId, wednesday, 2002, 3, 0, 0)); // 12 pts, not eligible

        WeekScoreResult result = scoringService.computeWeekScore(
                userId,
                List.of(slot),
                List.of(mondayStarted, wednesdayBenched),
                List.of(),
                performances,
                rules);

        assertThat(result.breakdown().totalPoints()).isEqualByComparingTo(new BigDecimal("8.00"));
        PlayerWeekScore line = result.playerScores().get(0);
        assertThat(line.slotActive()).isFalse();
        assertThat(line.scoringGamePk()).isEqualTo(2001);
        assertThat(line.games())
                .filteredOn(PlayerGamePerformance::eligible)
                .extracting(PlayerGamePerformance::gamePk)
                .containsExactly(2001);
    }

    @Test
    void performanceLockBeatsLaterHigherGame() {
        Integer playerId = 303;
        RosterSlot slot = new RosterSlot(leagueId, userId, playerId, "START", true);
        LocalDate monday = LocalDate.of(2026, 3, 23);
        LocalDate friday = LocalDate.of(2026, 3, 27);

        PerformanceLock lock = new PerformanceLock(leagueId, userId, 1, playerId, 3001, false);

        List<DailyPerformance> performances = List.of(
                performance(playerId, monday, 3001, 1, 0, 0),  // 4 pts, locked
                performance(playerId, friday, 3002, 3, 0, 0)); // 12 pts, would be best

        WeekScoreResult result = scoringService.computeWeekScore(
                userId, List.of(slot), List.of(), List.of(lock), performances, rules);

        assertThat(result.breakdown().totalPoints()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(result.playerScores().get(0).scoringGamePk()).isEqualTo(3001);
        assertThat(result.playerScores().get(0).performanceLocked()).isTrue();
    }

    @Test
    void inactiveWithoutEligibilityContributesZero() {
        Integer playerId = 404;
        RosterSlot slot = new RosterSlot(leagueId, userId, playerId, "BENCH", false);
        LocalDate monday = LocalDate.of(2026, 3, 23);

        WeekScoreResult result = scoringService.computeWeekScore(
                userId,
                List.of(slot),
                List.of(),
                List.of(),
                List.of(performance(playerId, monday, 4001, 2, 0, 0)),
                rules);

        assertThat(result.breakdown().totalPoints()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.playerScores().get(0).scoringGamePk()).isNull();
    }

    @Test
    void snapshotScoreIgnoresLiveRosterShape() {
        MatchupPlayerScore snap = new MatchupPlayerScore(
                UUID.randomUUID(),
                userId,
                505,
                true,
                new BigDecimal("12.00"),
                5001,
                Map.of("home_runs", new BigDecimal("12.00")));

        WeekScoreResult result = scoringService.computeWeekScoreFromSnapshot(userId, List.of(snap));

        assertThat(result.breakdown().totalPoints()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(result.breakdown().categoryPoints())
                .containsEntry("home_runs", new BigDecimal("12.00"));
        assertThat(result.playerScores()).hasSize(1);
        assertThat(result.playerScores().get(0).scoringGamePk()).isEqualTo(5001);
    }

    private DailyPerformance performance(int homeRuns, int rbi, int strikeoutsBatting) {
        return performance(1, LocalDate.of(2026, 3, 23), 1, homeRuns, rbi, strikeoutsBatting);
    }

    private DailyPerformance performance(int playerId,
                                         LocalDate gameDate,
                                         int gamePk,
                                         int homeRuns,
                                         int rbi,
                                         int strikeoutsBatting) {
        DailyPerformance p = newInstance(DailyPerformance.class);
        setField(p, "playerId", playerId);
        setField(p, "gameDate", gameDate);
        setField(p, "gamePk", gamePk);
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
