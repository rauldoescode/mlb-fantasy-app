package com.mlbfantasy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Per-league scoring configuration. {@code pointValues} maps a stat key
 * (e.g. "home_runs", "rbi", "strikeouts_batting") to the points awarded per unit.
 * Negative values are allowed (e.g. batter strikeouts).
 */
@Entity
@Table(name = "scoring_rules")
public class ScoringRule {

    @Id
    @Column(name = "league_id", updatable = false, nullable = false)
    private UUID leagueId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "point_values")
    private Map<String, Double> pointValues = new HashMap<>();

    protected ScoringRule() {
    }

    public ScoringRule(UUID leagueId, Map<String, Double> pointValues) {
        this.leagueId = leagueId;
        this.pointValues = pointValues;
    }

    public UUID getLeagueId() {
        return leagueId;
    }

    public Map<String, Double> getPointValues() {
        return pointValues;
    }

    public void setPointValues(Map<String, Double> pointValues) {
        this.pointValues = pointValues;
    }
}
