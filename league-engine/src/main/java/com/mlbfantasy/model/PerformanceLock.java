package com.mlbfantasy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Locks which single box score ({@code gamePk}) counts for a player in a given
 * H2H week. If absent, scoring uses the best eligible game so far.
 */
@Entity
@Table(name = "performance_locks")
public class PerformanceLock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "league_id", nullable = false)
    private UUID leagueId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "week_number", nullable = false)
    private int weekNumber;

    @Column(name = "player_id", nullable = false)
    private Integer playerId;

    @Column(name = "game_pk", nullable = false)
    private Integer gamePk;

    @CreationTimestamp
    @Column(name = "locked_at", nullable = false, updatable = false)
    private OffsetDateTime lockedAt;

    @Column(name = "auto_locked", nullable = false)
    private boolean autoLocked = false;

    protected PerformanceLock() {
    }

    public PerformanceLock(UUID leagueId,
                           UUID userId,
                           int weekNumber,
                           Integer playerId,
                           Integer gamePk,
                           boolean autoLocked) {
        this.leagueId = leagueId;
        this.userId = userId;
        this.weekNumber = weekNumber;
        this.playerId = playerId;
        this.gamePk = gamePk;
        this.autoLocked = autoLocked;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLeagueId() {
        return leagueId;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getWeekNumber() {
        return weekNumber;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public Integer getGamePk() {
        return gamePk;
    }

    public void setGamePk(Integer gamePk) {
        this.gamePk = gamePk;
    }

    public OffsetDateTime getLockedAt() {
        return lockedAt;
    }

    public boolean isAutoLocked() {
        return autoLocked;
    }

    public void setAutoLocked(boolean autoLocked) {
        this.autoLocked = autoLocked;
    }
}
