package com.mlbfantasy.service;

import com.mlbfantasy.dto.LockPerformanceRequest;
import com.mlbfantasy.dto.PerformanceLockResponse;
import com.mlbfantasy.dto.PlayerGamePerformance;
import com.mlbfantasy.dto.PlayerWeekScore;
import com.mlbfantasy.dto.WeekScoreResult;
import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.Matchup;
import com.mlbfantasy.model.PerformanceLock;
import com.mlbfantasy.model.RosterSlot;
import com.mlbfantasy.repository.MatchupRepository;
import com.mlbfantasy.repository.PerformanceLockRepository;
import com.mlbfantasy.repository.RosterSlotRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manual best-game performance locks for an open H2H week.
 *
 * <p>A lock pins which {@code game_pk} counts for a rostered player. When the
 * request omits {@code gamePk}, the current best eligible game is locked.
 * Unlocks are allowed until the week's matchup is {@code FINAL}.
 */
@Service
public class PerformanceLockService {

    private final PerformanceLockRepository performanceLockRepository;
    private final RosterSlotRepository rosterSlotRepository;
    private final MatchupRepository matchupRepository;
    private final LeagueService leagueService;
    private final ScoringService scoringService;
    private final WeekService weekService;

    public PerformanceLockService(PerformanceLockRepository performanceLockRepository,
                                  RosterSlotRepository rosterSlotRepository,
                                  MatchupRepository matchupRepository,
                                  LeagueService leagueService,
                                  ScoringService scoringService,
                                  WeekService weekService) {
        this.performanceLockRepository = performanceLockRepository;
        this.rosterSlotRepository = rosterSlotRepository;
        this.matchupRepository = matchupRepository;
        this.leagueService = leagueService;
        this.scoringService = scoringService;
        this.weekService = weekService;
    }

    @Transactional
    public PerformanceLockResponse lockPerformance(UUID leagueId,
                                                   int weekNumber,
                                                   Integer playerId,
                                                   UUID userId,
                                                   LockPerformanceRequest request) {
        requireOpenWeekOwnership(leagueId, weekNumber, playerId, userId);

        WeekScoreResult weekScore = scoringService.scoreWeekDetailed(leagueId, userId, weekNumber);
        PlayerWeekScore playerScore = weekScore.playerScores().stream()
                .filter(score -> playerId.equals(score.playerId()))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest(
                        "Player has no scoring line for this week"));

        Integer gamePk = resolveGamePk(request != null ? request.gamePk() : null, playerScore);

        PerformanceLock lock = performanceLockRepository
                .findByLeagueIdAndUserIdAndWeekNumberAndPlayerId(
                        leagueId, userId, weekNumber, playerId)
                .orElse(null);

        if (lock == null) {
            lock = new PerformanceLock(leagueId, userId, weekNumber, playerId, gamePk, false);
        } else {
            lock.setGamePk(gamePk);
            lock.setAutoLocked(false);
        }
        lock = performanceLockRepository.save(lock);
        return PerformanceLockResponse.from(lock);
    }

    @Transactional
    public void unlockPerformance(UUID leagueId, int weekNumber, Integer playerId, UUID userId) {
        requireOpenWeekOwnership(leagueId, weekNumber, playerId, userId);

        performanceLockRepository
                .findByLeagueIdAndUserIdAndWeekNumberAndPlayerId(
                        leagueId, userId, weekNumber, playerId)
                .ifPresent(performanceLockRepository::delete);
    }

    /**
     * Auto-lock the best eligible game for finalize. No-op when a manual lock
     * already exists or there is no eligible game.
     */
    @Transactional
    public void autoLockBestIfAbsent(UUID leagueId, int weekNumber, UUID userId, Integer playerId) {
        if (playerId == null) {
            return;
        }
        if (performanceLockRepository
                .findByLeagueIdAndUserIdAndWeekNumberAndPlayerId(
                        leagueId, userId, weekNumber, playerId)
                .isPresent()) {
            return;
        }

        WeekScoreResult weekScore = scoringService.scoreWeekDetailed(leagueId, userId, weekNumber);
        PlayerWeekScore playerScore = weekScore.playerScores().stream()
                .filter(score -> playerId.equals(score.playerId()))
                .findFirst()
                .orElse(null);
        if (playerScore == null) {
            return;
        }

        PlayerGamePerformance best = ScoringService.selectBestEligibleGame(playerScore.games());
        if (best == null || best.gamePk() == null) {
            return;
        }

        performanceLockRepository.save(
                new PerformanceLock(leagueId, userId, weekNumber, playerId, best.gamePk(), true));
    }

    private void requireOpenWeekOwnership(UUID leagueId,
                                          int weekNumber,
                                          Integer playerId,
                                          UUID userId) {
        leagueService.requireMember(leagueId, userId);
        if (weekNumber < 1 || weekNumber > weekService.totalWeeks()) {
            throw ApiException.badRequest(
                    "Week must be between 1 and " + weekService.totalWeeks());
        }
        if (weekNumber != weekService.currentSeasonWeek()) {
            throw ApiException.conflict(
                    "Performance locks can only be changed for the current week");
        }

        Matchup matchup = matchupRepository.findForUserInWeek(leagueId, weekNumber, userId)
                .orElse(null);
        if (matchup != null && "FINAL".equals(matchup.getStatus())) {
            throw ApiException.conflict(
                    "This week's matchup is final; performance locks are frozen");
        }

        RosterSlot slot = rosterSlotRepository
                .findByLeagueIdAndUserIdAndPlayerId(leagueId, userId, playerId)
                .orElseThrow(() -> ApiException.notFound(
                        "Player is not on your roster in this league"));
        if (slot.getPlayerId() == null) {
            throw ApiException.badRequest("Roster slot has no player");
        }
    }

    private static Integer resolveGamePk(Integer requestedGamePk, PlayerWeekScore playerScore) {
        if (requestedGamePk != null) {
            PlayerGamePerformance match = playerScore.games().stream()
                    .filter(game -> requestedGamePk.equals(game.gamePk()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest(
                            "gamePk is not among this player's games for the week"));
            if (!match.eligible()) {
                throw ApiException.badRequest(
                        "Cannot lock a game the player was not started for");
            }
            return requestedGamePk;
        }

        PlayerGamePerformance best = ScoringService.selectBestEligibleGame(playerScore.games());
        if (best == null || best.gamePk() == null) {
            throw ApiException.badRequest(
                    "No eligible game to lock for this player yet");
        }
        return best.gamePk();
    }
}
