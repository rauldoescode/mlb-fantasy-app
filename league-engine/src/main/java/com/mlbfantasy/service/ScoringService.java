package com.mlbfantasy.service;

import com.mlbfantasy.dto.ScoreBreakdown;
import com.mlbfantasy.model.DailyPerformance;
import com.mlbfantasy.model.RosterSlot;
import com.mlbfantasy.model.ScoringRule;
import com.mlbfantasy.repository.DailyPerformanceRepository;
import com.mlbfantasy.repository.RosterSlotRepository;
import com.mlbfantasy.repository.ScoringRuleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * Computes weekly H2H points for a user's active roster.
 *
 * <p>For each stat category configured in the league's {@code scoring_rules}, the
 * raw stat is summed across every active roster player's box scores within the
 * Monday-Sunday window and multiplied by the configured point value.
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
    private final WeekService weekService;

    public ScoringService(RosterSlotRepository rosterSlotRepository,
                          DailyPerformanceRepository dailyPerformanceRepository,
                          ScoringRuleRepository scoringRuleRepository,
                          WeekService weekService) {
        this.rosterSlotRepository = rosterSlotRepository;
        this.dailyPerformanceRepository = dailyPerformanceRepository;
        this.scoringRuleRepository = scoringRuleRepository;
        this.weekService = weekService;
    }

    public ScoreBreakdown scoreWeek(UUID leagueId, UUID userId, int weekNumber) {
        Map<String, Double> pointValues = resolvePointValues(leagueId);
        LocalDate start = weekService.weekStart(weekNumber);
        LocalDate end = weekService.weekEnd(weekNumber);

        List<Integer> playerIds = rosterSlotRepository
                .findByLeagueIdAndUserIdAndActiveTrue(leagueId, userId).stream()
                .map(RosterSlot::getPlayerId)
                .filter(Objects::nonNull)
                .toList();

        if (playerIds.isEmpty()) {
            return new ScoreBreakdown(userId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    new LinkedHashMap<>());
        }

        List<DailyPerformance> performances = dailyPerformanceRepository
                .findByPlayerIdInAndGameDateBetween(playerIds, start, end);

        return computeBreakdown(userId, performances, pointValues);
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

    private static BigDecimal intToBig(Integer value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }
}
