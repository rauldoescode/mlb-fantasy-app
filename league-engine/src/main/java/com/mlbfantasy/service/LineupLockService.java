package com.mlbfantasy.service;

import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.LineupEligibility;
import com.mlbfantasy.model.Matchup;
import com.mlbfantasy.model.PlayerScheduledGame;
import com.mlbfantasy.repository.LineupEligibilityRepository;
import com.mlbfantasy.repository.MatchupRepository;
import com.mlbfantasy.repository.PlayerScheduledGameRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lineup lock rules and daily eligibility snapshots.
 *
 * <p>A player locks once any of their games scheduled for "today" (league zone)
 * has reached its start time. Before first pitch the slot is editable; after
 * first pitch, today's eligibility row is frozen ({@code lockedAt} set) and
 * Start/Bench changes are rejected.
 *
 * <p>Start/Bench is also rejected when the caller's matchup for the current
 * season week is already {@code FINAL}.
 */
@Service
public class LineupLockService {

    private final PlayerScheduledGameRepository scheduledGameRepository;
    private final LineupEligibilityRepository lineupEligibilityRepository;
    private final MatchupRepository matchupRepository;
    private final WeekService weekService;

    public LineupLockService(PlayerScheduledGameRepository scheduledGameRepository,
                             LineupEligibilityRepository lineupEligibilityRepository,
                             MatchupRepository matchupRepository,
                             WeekService weekService) {
        this.scheduledGameRepository = scheduledGameRepository;
        this.lineupEligibilityRepository = lineupEligibilityRepository;
        this.matchupRepository = matchupRepository;
        this.weekService = weekService;
    }

    public boolean isPlayerLocked(Integer playerId) {
        if (playerId == null) {
            return false;
        }
        LocalDate today = LocalDate.now(weekService.zone());
        OffsetDateTime now = OffsetDateTime.now();
        return scheduledGameRepository.findByPlayerIdAndGameDate(playerId, today).stream()
                .map(PlayerScheduledGame::getGameStartTime)
                .anyMatch(start -> start != null && !start.isAfter(now));
    }

    /**
     * Rejects Start/Bench when the user's current-week matchup is already final.
     */
    public void requireLineupEditable(UUID leagueId, UUID userId) {
        int week = weekService.currentSeasonWeek();
        matchupRepository.findForUserInWeek(leagueId, week, userId)
                .map(Matchup::getStatus)
                .filter("FINAL"::equals)
                .ifPresent(status -> {
                    throw ApiException.conflict(
                            "This week's matchup is final; lineup changes are locked");
                });
    }

    /**
     * Matchup-card Start/Bench: only the participant may edit, only for the current
     * season week, and only while the matchup is not FINAL.
     */
    public void requireMatchupLineupEditable(Matchup matchup, UUID userId) {
        if (!userId.equals(matchup.getUserOneId()) && !userId.equals(matchup.getUserTwoId())) {
            throw ApiException.forbidden("You are not a participant in this matchup");
        }
        if (matchup.getWeekNumber() != weekService.currentSeasonWeek()) {
            throw ApiException.conflict(
                    "Lineup changes are only allowed for the current week");
        }
        if ("FINAL".equals(matchup.getStatus())) {
            throw ApiException.conflict(
                    "This week's matchup is final; lineup changes are locked");
        }
    }

    /** Whether the requester can Start/Bench their own players on this matchup card. */
    public boolean isMatchupLineupEditable(Matchup matchup, UUID requesterId) {
        if (!requesterId.equals(matchup.getUserOneId())
                && !requesterId.equals(matchup.getUserTwoId())) {
            return false;
        }
        if (matchup.getWeekNumber() != weekService.currentSeasonWeek()) {
            return false;
        }
        return !"FINAL".equals(matchup.getStatus());
    }

    /**
     * Upserts eligibility for today through Sunday of the current season week.
     * Frozen rows ({@code lockedAt != null}) are left unchanged. If today is
     * locked and no row exists yet, materializes a frozen row from
     * {@code wasStarted}.
     */
    @Transactional
    public void syncOpenWeekEligibility(UUID leagueId,
                                        UUID userId,
                                        Integer playerId,
                                        boolean wasStarted) {
        if (playerId == null) {
            return;
        }
        int week = weekService.currentSeasonWeek();
        LocalDate today = LocalDate.now(weekService.zone());
        LocalDate weekStart = weekService.weekStart(week);
        LocalDate weekEnd = weekService.weekEnd(week);

        if (today.isBefore(weekStart) || today.isAfter(weekEnd)) {
            // Outside the configured season window — nothing to snapshot.
            return;
        }

        boolean lockedToday = isPlayerLocked(playerId);
        for (LocalDate date = today; !date.isAfter(weekEnd); date = date.plusDays(1)) {
            if (date.equals(today) && lockedToday) {
                freezeTodaysEligibility(leagueId, userId, week, playerId, today, wasStarted);
            } else {
                upsertUnlockedEligibility(leagueId, userId, week, playerId, date, wasStarted);
            }
        }
    }

    /**
     * If the player is locked for today and eligibility is missing or unlocked,
     * freeze a row from the current active/bench state. Safe to call on roster reads.
     */
    @Transactional
    public void materializeTodaysLockIfNeeded(UUID leagueId,
                                             UUID userId,
                                             Integer playerId,
                                             boolean currentlyActive) {
        if (playerId == null || !isPlayerLocked(playerId)) {
            return;
        }
        int week = weekService.currentSeasonWeek();
        LocalDate today = LocalDate.now(weekService.zone());
        if (today.isBefore(weekService.weekStart(week)) || today.isAfter(weekService.weekEnd(week))) {
            return;
        }
        freezeTodaysEligibility(leagueId, userId, week, playerId, today, currentlyActive);
    }

    private void freezeTodaysEligibility(UUID leagueId,
                                         UUID userId,
                                         int week,
                                         Integer playerId,
                                         LocalDate today,
                                         boolean wasStartedFallback) {
        LineupEligibility row = lineupEligibilityRepository
                .findByLeagueIdAndUserIdAndWeekNumberAndPlayerIdAndGameDate(
                        leagueId, userId, week, playerId, today)
                .orElse(null);

        if (row == null) {
            row = new LineupEligibility(leagueId, userId, week, playerId, today, wasStartedFallback);
            row.setLockedAt(OffsetDateTime.now());
            lineupEligibilityRepository.save(row);
            return;
        }

        if (row.getLockedAt() == null) {
            // Freeze whatever was already written (or fall back if somehow empty).
            row.setLockedAt(OffsetDateTime.now());
            lineupEligibilityRepository.save(row);
        }
        // Already frozen — do not rewrite wasStarted.
    }

    private void upsertUnlockedEligibility(UUID leagueId,
                                           UUID userId,
                                           int week,
                                           Integer playerId,
                                           LocalDate date,
                                           boolean wasStarted) {
        LineupEligibility row = lineupEligibilityRepository
                .findByLeagueIdAndUserIdAndWeekNumberAndPlayerIdAndGameDate(
                        leagueId, userId, week, playerId, date)
                .orElse(null);

        if (row == null) {
            lineupEligibilityRepository.save(
                    new LineupEligibility(leagueId, userId, week, playerId, date, wasStarted));
            return;
        }

        if (row.getLockedAt() != null) {
            return; // Frozen historical / same-day lock — leave alone.
        }

        if (row.isWasStarted() != wasStarted) {
            row.setWasStarted(wasStarted);
            lineupEligibilityRepository.save(row);
        }
    }
}
