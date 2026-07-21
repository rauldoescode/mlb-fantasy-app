package com.mlbfantasy.service;

import com.mlbfantasy.dto.PlayerGamePerformance;
import com.mlbfantasy.dto.PlayerWeekScore;
import com.mlbfantasy.dto.ScoreBreakdown;
import com.mlbfantasy.dto.WeekScoreResult;
import com.mlbfantasy.model.DailyPerformance;
import com.mlbfantasy.model.LineupEligibility;
import com.mlbfantasy.model.MatchupPlayerScore;
import com.mlbfantasy.model.PerformanceLock;
import com.mlbfantasy.model.RosterSlot;
import com.mlbfantasy.model.ScoringRule;
import com.mlbfantasy.repository.DailyPerformanceRepository;
import com.mlbfantasy.repository.LineupEligibilityRepository;
import com.mlbfantasy.repository.MatchupPlayerScoreRepository;
import com.mlbfantasy.repository.PerformanceLockRepository;
import com.mlbfantasy.repository.RosterSlotRepository;
import com.mlbfantasy.repository.ScoringRuleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Computes weekly H2H points under best-game / performance-lock rules.
 *
 * <p>For each rostered player, every box score in the Monday–Sunday window is
 * scored. A game is <em>eligible</em> when {@code lineup_eligibility} says the
 * player was started that day, or — when no eligibility row exists yet — when
 * the player's current roster slot is active. The player's weekly contribution
 * is a single game: the locked {@code game_pk} if present, otherwise the highest
 * eligible game. Team totals are the sum of those per-player contributions.
 */
@Service
public class ScoringService {

    /** Default formula from the project blueprint, used when a league has no rules yet. */
    public static final Map<String, Double> DEFAULT_POINT_VALUES = Map.of(
            "home_runs", 4.0,
            "rbi", 1.0,
            "stolen_bases", 2.0,
            "pitching_wins", 5.0,
            "strikeouts_batting", -1.0);

    /** Maps a scoring-rule key to the matching numeric extractor on a box score. */
    private static final Map<String, Function<DailyPerformance, BigDecimal>> STAT_EXTRACTORS =
            Map.of(
                    "hits", p -> intToBig(p.getHits()),
                    "home_runs", p -> intToBig(p.getHomeRuns()),
                    "rbi", p -> intToBig(p.getRbi()),
                    "stolen_bases", p -> intToBig(p.getStolenBases()),
                    "strikeouts_batting", p -> intToBig(p.getStrikeoutsBatting()),
                    "innings_pitched", p -> p.getInningsPitched() == null
                            ? BigDecimal.ZERO : p.getInningsPitched(),
                    "earned_runs", p -> intToBig(p.getEarnedRuns()),
                    "pitching_wins", p -> intToBig(p.getPitchingWins()),
                    "strikeouts_pitching", p -> intToBig(p.getStrikeoutsPitching()));

    private final RosterSlotRepository rosterSlotRepository;
    private final DailyPerformanceRepository dailyPerformanceRepository;
    private final ScoringRuleRepository scoringRuleRepository;
    private final LineupEligibilityRepository lineupEligibilityRepository;
    private final PerformanceLockRepository performanceLockRepository;
    private final MatchupPlayerScoreRepository matchupPlayerScoreRepository;
    private final WeekService weekService;

    public ScoringService(RosterSlotRepository rosterSlotRepository,
                          DailyPerformanceRepository dailyPerformanceRepository,
                          ScoringRuleRepository scoringRuleRepository,
                          LineupEligibilityRepository lineupEligibilityRepository,
                          PerformanceLockRepository performanceLockRepository,
                          MatchupPlayerScoreRepository matchupPlayerScoreRepository,
                          WeekService weekService) {
        this.rosterSlotRepository = rosterSlotRepository;
        this.dailyPerformanceRepository = dailyPerformanceRepository;
        this.scoringRuleRepository = scoringRuleRepository;
        this.lineupEligibilityRepository = lineupEligibilityRepository;
        this.performanceLockRepository = performanceLockRepository;
        this.matchupPlayerScoreRepository = matchupPlayerScoreRepository;
        this.weekService = weekService;
    }

    /** Team category breakdown for an open (live) week. */
    public ScoreBreakdown scoreWeek(UUID leagueId, UUID userId, int weekNumber) {
        return scoreWeekDetailed(leagueId, userId, weekNumber).breakdown();
    }

    /** Live week score including per-player best/locked game lines. */
    public WeekScoreResult scoreWeekDetailed(UUID leagueId, UUID userId, int weekNumber) {
        Map<String, Double> pointValues = resolvePointValues(leagueId);
        LocalDate start = weekService.weekStart(weekNumber);
        LocalDate end = weekService.weekEnd(weekNumber);

        List<RosterSlot> slots = rosterSlotRepository.findByLeagueIdAndUserId(leagueId, userId);
        List<Integer> playerIds = slots.stream()
                .map(RosterSlot::getPlayerId)
                .filter(Objects::nonNull)
                .toList();

        List<DailyPerformance> performances = playerIds.isEmpty()
                ? List.of()
                : dailyPerformanceRepository.findByPlayerIdInAndGameDateBetween(playerIds, start, end);

        List<LineupEligibility> eligibility = lineupEligibilityRepository
                .findByLeagueIdAndUserIdAndWeekNumber(leagueId, userId, weekNumber);
        List<PerformanceLock> locks = performanceLockRepository
                .findByLeagueIdAndUserIdAndWeekNumber(leagueId, userId, weekNumber);

        return computeWeekScore(userId, slots, eligibility, locks, performances, pointValues);
    }

    /**
     * Reconstructs a user's week score from finalize snapshots. Used for FINAL
     * matchups so live roster edits cannot rewrite history.
     */
    public WeekScoreResult scoreWeekFromSnapshot(UUID matchupId, UUID userId) {
        List<MatchupPlayerScore> rows =
                matchupPlayerScoreRepository.findByMatchupIdAndUserId(matchupId, userId);
        return computeWeekScoreFromSnapshot(userId, rows);
    }

    /** Pure snapshot aggregation — no I/O. */
    public WeekScoreResult computeWeekScoreFromSnapshot(UUID userId, List<MatchupPlayerScore> rows) {
        Map<String, BigDecimal> categoryPoints = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<PlayerWeekScore> playerScores = new ArrayList<>();

        for (MatchupPlayerScore row : rows) {
            Map<String, BigDecimal> cats = row.getCategoryPoints() != null
                    ? new LinkedHashMap<>(row.getCategoryPoints())
                    : new LinkedHashMap<>();
            for (Map.Entry<String, BigDecimal> entry : cats.entrySet()) {
                categoryPoints.merge(entry.getKey(), entry.getValue(), BigDecimal::add);
            }
            BigDecimal points = row.getPoints() != null
                    ? row.getPoints().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            total = total.add(points);
            playerScores.add(new PlayerWeekScore(
                    row.getPlayerId(),
                    row.isSlotActive(),
                    points,
                    row.getGamePk(),
                    row.getGamePk() != null,
                    cats,
                    List.of()));
        }

        Map<String, BigDecimal> scaledCategories = new LinkedHashMap<>();
        categoryPoints.forEach((k, v) -> scaledCategories.put(k, v.setScale(2, RoundingMode.HALF_UP)));

        return new WeekScoreResult(
                new ScoreBreakdown(userId, total.setScale(2, RoundingMode.HALF_UP), scaledCategories),
                List.copyOf(playerScores));
    }

    /**
     * Scores each box score independently. Eligibility is not applied here —
     * callers set {@code eligible} via {@link #scorePlayerGames(List, Map, java.util.function.Predicate)}.
     */
    public List<PlayerGamePerformance> scorePlayerGames(List<DailyPerformance> performances,
                                                        Map<String, Double> pointValues) {
        return scorePlayerGames(performances, pointValues, p -> true);
    }

    public List<PlayerGamePerformance> scorePlayerGames(List<DailyPerformance> performances,
                                                        Map<String, Double> pointValues,
                                                        java.util.function.Predicate<DailyPerformance> eligible) {
        List<PlayerGamePerformance> scored = new ArrayList<>();
        for (DailyPerformance performance : performances) {
            ScoreBreakdown breakdown = computeBreakdown(null, List.of(performance), pointValues);
            scored.add(new PlayerGamePerformance(
                    performance.getGamePk(),
                    performance.getGameDate(),
                    breakdown.totalPoints(),
                    breakdown.categoryPoints(),
                    eligible.test(performance)));
        }
        return scored;
    }

    /**
     * Pure week scoring: eligibility + best/locked game selection. No I/O.
     */
    public WeekScoreResult computeWeekScore(UUID userId,
                                            List<RosterSlot> slots,
                                            List<LineupEligibility> eligibilityRows,
                                            List<PerformanceLock> locks,
                                            List<DailyPerformance> performances,
                                            Map<String, Double> pointValues) {
        Map<Integer, Boolean> activeByPlayer = slots.stream()
                .filter(slot -> slot.getPlayerId() != null)
                .collect(Collectors.toMap(
                        RosterSlot::getPlayerId,
                        slot -> Boolean.TRUE.equals(slot.getActive()),
                        (a, b) -> a,
                        LinkedHashMap::new));

        Map<String, Boolean> startedByPlayerDate = new HashMap<>();
        for (LineupEligibility row : eligibilityRows) {
            startedByPlayerDate.put(eligibilityKey(row.getPlayerId(), row.getGameDate()), row.isWasStarted());
        }

        Map<Integer, Integer> lockedGameByPlayer = locks.stream()
                .collect(Collectors.toMap(
                        PerformanceLock::getPlayerId,
                        PerformanceLock::getGamePk,
                        (a, b) -> a));

        Map<Integer, List<DailyPerformance>> performancesByPlayer = performances.stream()
                .filter(p -> p.getPlayerId() != null)
                .collect(Collectors.groupingBy(DailyPerformance::getPlayerId));

        Map<String, BigDecimal> teamCategories = new LinkedHashMap<>();
        for (String key : pointValues.keySet()) {
            if (STAT_EXTRACTORS.containsKey(key)) {
                teamCategories.put(key, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }
        }

        BigDecimal teamTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<PlayerWeekScore> playerScores = new ArrayList<>();

        for (Map.Entry<Integer, Boolean> entry : activeByPlayer.entrySet()) {
            Integer playerId = entry.getKey();
            boolean slotActive = entry.getValue();
            List<DailyPerformance> playerGames = performancesByPlayer.getOrDefault(playerId, List.of());

            List<PlayerGamePerformance> games = scorePlayerGames(
                    playerGames,
                    pointValues,
                    p -> isEligible(playerId, p.getGameDate(), startedByPlayerDate, slotActive));

            Integer lockedGamePk = lockedGameByPlayer.get(playerId);
            PlayerGamePerformance scoringGame = selectScoringGame(games, lockedGamePk);
            boolean locked = lockedGamePk != null;

            BigDecimal points = scoringGame != null
                    ? scoringGame.points()
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            Map<String, BigDecimal> cats = scoringGame != null
                    ? scoringGame.categoryPoints()
                    : zeroCategories(pointValues);

            if (scoringGame != null) {
                teamTotal = teamTotal.add(points);
                for (Map.Entry<String, BigDecimal> cat : cats.entrySet()) {
                    teamCategories.merge(cat.getKey(), cat.getValue(), BigDecimal::add);
                }
            }

            playerScores.add(new PlayerWeekScore(
                    playerId,
                    slotActive,
                    points,
                    scoringGame != null ? scoringGame.gamePk() : null,
                    locked && scoringGame != null && lockedGamePk.equals(scoringGame.gamePk()),
                    cats,
                    games));
        }

        Map<String, BigDecimal> scaledCategories = new LinkedHashMap<>();
        teamCategories.forEach((k, v) -> scaledCategories.put(k, v.setScale(2, RoundingMode.HALF_UP)));

        return new WeekScoreResult(
                new ScoreBreakdown(userId, teamTotal.setScale(2, RoundingMode.HALF_UP), scaledCategories),
                List.copyOf(playerScores));
    }

    /**
     * Pure scoring: sum each configured stat across the given box scores and apply its
     * point value. Stat keys not understood by the engine are ignored. This contains no
     * I/O so it can be unit-tested directly.
     */
    public ScoreBreakdown computeBreakdown(UUID userId,
                                           List<DailyPerformance> performances,
                                           Map<String, Double> pointValues) {
        Map<String, BigDecimal> categoryPoints = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, Double> rule : pointValues.entrySet()) {
            Function<DailyPerformance, BigDecimal> extractor = STAT_EXTRACTORS.get(rule.getKey());
            if (extractor == null) {
                continue; // Unknown stat key in the rules -> ignore rather than fail.
            }
            BigDecimal statTotal = BigDecimal.ZERO;
            for (DailyPerformance performance : performances) {
                statTotal = statTotal.add(extractor.apply(performance));
            }
            BigDecimal categoryTotal = statTotal
                    .multiply(BigDecimal.valueOf(rule.getValue()))
                    .setScale(2, RoundingMode.HALF_UP);
            categoryPoints.put(rule.getKey(), categoryTotal);
            total = total.add(categoryTotal);
        }
        return new ScoreBreakdown(userId, total.setScale(2, RoundingMode.HALF_UP), categoryPoints);
    }

    private Map<String, Double> resolvePointValues(UUID leagueId) {
        return scoringRuleRepository.findById(leagueId)
                .map(ScoringRule::getPointValues)
                .filter(values -> values != null && !values.isEmpty())
                .orElse(DEFAULT_POINT_VALUES);
    }

    /** Stat keys the engine knows how to score (useful for validating rule payloads). */
    public static List<String> supportedStatKeys() {
        return new ArrayList<>(STAT_EXTRACTORS.keySet());
    }

    static boolean isEligible(Integer playerId,
                              LocalDate gameDate,
                              Map<String, Boolean> startedByPlayerDate,
                              boolean currentlyActive) {
        Boolean started = startedByPlayerDate.get(eligibilityKey(playerId, gameDate));
        if (started != null) {
            return started;
        }
        // No snapshot yet — fall back to live active/bench so open weeks still score.
        return currentlyActive;
    }

    /**
     * Prefer the locked game when present (even if a later game scored higher).
     * Otherwise pick the best eligible game; ties break by earlier date, then lower game_pk.
     */
    static PlayerGamePerformance selectScoringGame(List<PlayerGamePerformance> games,
                                                   Integer lockedGamePk) {
        if (games == null || games.isEmpty()) {
            return null;
        }
        if (lockedGamePk != null) {
            for (PlayerGamePerformance game : games) {
                if (lockedGamePk.equals(game.gamePk())) {
                    return game;
                }
            }
            // Locked game_pk not found in this week's box scores — fall through to best eligible.
        }
        return selectBestEligibleGame(games);
    }

    /**
     * Best eligible game: highest points; on ties prefer earlier game_date then lower game_pk.
     */
    static PlayerGamePerformance selectBestEligibleGame(List<PlayerGamePerformance> games) {
        PlayerGamePerformance best = null;
        for (PlayerGamePerformance game : games) {
            if (!game.eligible()) {
                continue;
            }
            if (best == null || isBetterGame(game, best)) {
                best = game;
            }
        }
        return best;
    }

    /** Returns true if {@code candidate} should replace {@code current} as the scoring game. */
    private static boolean isBetterGame(PlayerGamePerformance candidate, PlayerGamePerformance current) {
        int pointsCmp = candidate.points().compareTo(current.points());
        if (pointsCmp != 0) {
            return pointsCmp > 0;
        }
        int dateCmp = candidate.gameDate().compareTo(current.gameDate());
        if (dateCmp != 0) {
            return dateCmp < 0; // earlier date wins ties
        }
        Integer candidatePk = candidate.gamePk() != null ? candidate.gamePk() : Integer.MAX_VALUE;
        Integer currentPk = current.gamePk() != null ? current.gamePk() : Integer.MAX_VALUE;
        return candidatePk < currentPk;
    }

    private static String eligibilityKey(Integer playerId, LocalDate gameDate) {
        return playerId + "|" + gameDate;
    }

    private static Map<String, BigDecimal> zeroCategories(Map<String, Double> pointValues) {
        Map<String, BigDecimal> cats = new LinkedHashMap<>();
        for (String key : pointValues.keySet()) {
            if (STAT_EXTRACTORS.containsKey(key)) {
                cats.put(key, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }
        }
        return cats;
    }

    private static BigDecimal intToBig(Integer value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }
}
