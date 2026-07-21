package com.mlbfantasy.dto;

import com.mlbfantasy.model.PerformanceLock;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PerformanceLockResponse(
        UUID id,
        UUID leagueId,
        UUID userId,
        int weekNumber,
        Integer playerId,
        Integer gamePk,
        boolean autoLocked,
        OffsetDateTime lockedAt) {

    public static PerformanceLockResponse from(PerformanceLock lock) {
        return new PerformanceLockResponse(
                lock.getId(),
                lock.getLeagueId(),
                lock.getUserId(),
                lock.getWeekNumber(),
                lock.getPlayerId(),
                lock.getGamePk(),
                lock.isAutoLocked(),
                lock.getLockedAt());
    }
}
