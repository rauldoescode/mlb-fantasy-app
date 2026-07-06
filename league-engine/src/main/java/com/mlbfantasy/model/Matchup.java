package com.mlbfantasy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "matchups")
public class Matchup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "league_id")
    private UUID leagueId;

    @Column(name = "week_number", nullable = false)
    private int weekNumber;

    @Column(name = "user_one_id")
    private UUID userOneId;

    @Column(name = "user_one_score")
    private BigDecimal userOneScore = BigDecimal.ZERO;

    @Column(name = "user_two_id")
    private UUID userTwoId;

    @Column(name = "user_two_score")
    private BigDecimal userTwoScore = BigDecimal.ZERO;

    @Column(name = "winner_id")
    private UUID winnerId;

    @Column(name = "status", nullable = false)
    private String status = "SCHEDULED";

    protected Matchup() {
    }

    public Matchup(UUID leagueId, int weekNumber, UUID userOneId, UUID userTwoId) {
        this.leagueId = leagueId;
        this.weekNumber = weekNumber;
        this.userOneId = userOneId;
        this.userTwoId = userTwoId;
        this.userOneScore = BigDecimal.ZERO;
        this.userTwoScore = BigDecimal.ZERO;
        this.status = "SCHEDULED";
    }

    public UUID getId() {
        return id;
    }

    public UUID getLeagueId() {
        return leagueId;
    }

    public int getWeekNumber() {
        return weekNumber;
    }

    public UUID getUserOneId() {
        return userOneId;
    }

    public BigDecimal getUserOneScore() {
        return userOneScore;
    }

    public void setUserOneScore(BigDecimal userOneScore) {
        this.userOneScore = userOneScore;
    }

    public UUID getUserTwoId() {
        return userTwoId;
    }

    public BigDecimal getUserTwoScore() {
        return userTwoScore;
    }

    public void setUserTwoScore(BigDecimal userTwoScore) {
        this.userTwoScore = userTwoScore;
    }

    public UUID getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(UUID winnerId) {
        this.winnerId = winnerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
