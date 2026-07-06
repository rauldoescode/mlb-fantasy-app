package com.mlbfantasy.service;

import com.mlbfantasy.model.PlayerScheduledGame;
import com.mlbfantasy.repository.PlayerScheduledGameRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

/**
 * Determines whether a roster slot is locked for lineup changes.
 *
 * <p>A player locks once any of their games scheduled for "today" (league zone)
 * has reached its start time. Before the first game starts the slot is editable;
 * after first pitch the active/bench status is frozen for the day. If no schedule
 * row exists for the player, the slot is considered unlocked (no game today).
 */
@Service
public class LineupLockService {

    private final PlayerScheduledGameRepository scheduledGameRepository;
    private final WeekService weekService;

    public LineupLockService(PlayerScheduledGameRepository scheduledGameRepository,
                             WeekService weekService) {
        this.scheduledGameRepository = scheduledGameRepository;
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
}
