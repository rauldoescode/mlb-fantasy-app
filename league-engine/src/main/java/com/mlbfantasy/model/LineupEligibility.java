package com.mlbfantasy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Records whether a rostered player was started on a given game date within a
 * H2H week. Once {@code lockedAt} is set (typically at first pitch), the row is
 * treated as frozen for scoring.
 */
@Entity
@Table(name = "lineup_eligibility")
public class LineupEligibility {

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

    @Column(name = "game_date", nullable = false)
    private LocalDate gameDate;

    @Column(name = "was_started", nullable = false)
    private boolean wasStarted;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    protected LineupEligibility() {
    }

    public LineupEligibility(UUID leagueId,
                             UUID userId,
                             int weekNumber,
                             Integer playerId,
                             LocalDate gameDate,
                             boolean wasStarted) {
        this.leagueId = leagueId;
        this.userId = userId;
        this.weekNumber = weekNumber;
        this.playerId = playerId;
        this.gameDate = gameDate;
        this.wasStarted = wasStarted;
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

    public LocalDate getGameDate() {
        return gameDate;
    }

    public boolean isWasStarted() {
        return wasStarted;
    }

    public void setWasStarted(boolean wasStarted) {
        this.wasStarted = wasStarted;
    }

    public OffsetDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(OffsetDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }
}
