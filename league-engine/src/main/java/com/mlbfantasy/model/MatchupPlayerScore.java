package com.mlbfantasy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Immutable per-player score row written when a matchup is finalized. Detail
 * reads for FINAL weeks use these rows instead of live roster / eligibility.
 */
@Entity
@Table(name = "matchup_player_scores")
public class MatchupPlayerScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "matchup_id", nullable = false)
    private UUID matchupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "player_id", nullable = false)
    private Integer playerId;

    @Column(name = "slot_active", nullable = false)
    private boolean slotActive;

    @Column(name = "points", nullable = false)
    private BigDecimal points = BigDecimal.ZERO;

    @Column(name = "game_pk")
    private Integer gamePk;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_points")
    private Map<String, BigDecimal> categoryPoints = new HashMap<>();

    protected MatchupPlayerScore() {
    }

    public MatchupPlayerScore(UUID matchupId,
                              UUID userId,
                              Integer playerId,
                              boolean slotActive,
                              BigDecimal points,
                              Integer gamePk,
                              Map<String, BigDecimal> categoryPoints) {
        this.matchupId = matchupId;
        this.userId = userId;
        this.playerId = playerId;
        this.slotActive = slotActive;
        this.points = points;
        this.gamePk = gamePk;
        this.categoryPoints = categoryPoints != null ? categoryPoints : new HashMap<>();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMatchupId() {
        return matchupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public boolean isSlotActive() {
        return slotActive;
    }

    public BigDecimal getPoints() {
        return points;
    }

    public Integer getGamePk() {
        return gamePk;
    }

    public Map<String, BigDecimal> getCategoryPoints() {
        return categoryPoints;
    }
}
