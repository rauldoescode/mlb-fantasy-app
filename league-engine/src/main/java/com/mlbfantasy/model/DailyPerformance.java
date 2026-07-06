package com.mlbfantasy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "daily_performances")
public class DailyPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "player_id")
    private Integer playerId;

    @Column(name = "game_date", nullable = false)
    private LocalDate gameDate;

    @Column(name = "game_pk", nullable = false)
    private Integer gamePk;

    @Column(name = "hits")
    private Integer hits;

    @Column(name = "home_runs")
    private Integer homeRuns;

    @Column(name = "rbi")
    private Integer rbi;

    @Column(name = "stolen_bases")
    private Integer stolenBases;

    @Column(name = "strikeouts_batting")
    private Integer strikeoutsBatting;

    @Column(name = "innings_pitched")
    private BigDecimal inningsPitched;

    @Column(name = "earned_runs")
    private Integer earnedRuns;

    @Column(name = "pitching_wins")
    private Integer pitchingWins;

    @Column(name = "strikeouts_pitching")
    private Integer strikeoutsPitching;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_stats")
    private Map<String, Object> rawStats;

    protected DailyPerformance() {
    }

    public Long getId() {
        return id;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public LocalDate getGameDate() {
        return gameDate;
    }

    public Integer getGamePk() {
        return gamePk;
    }

    public Integer getHits() {
        return hits;
    }

    public Integer getHomeRuns() {
        return homeRuns;
    }

    public Integer getRbi() {
        return rbi;
    }

    public Integer getStolenBases() {
        return stolenBases;
    }

    public Integer getStrikeoutsBatting() {
        return strikeoutsBatting;
    }

    public BigDecimal getInningsPitched() {
        return inningsPitched;
    }

    public Integer getEarnedRuns() {
        return earnedRuns;
    }

    public Integer getPitchingWins() {
        return pitchingWins;
    }

    public Integer getStrikeoutsPitching() {
        return strikeoutsPitching;
    }

    public Map<String, Object> getRawStats() {
        return rawStats;
    }
}
