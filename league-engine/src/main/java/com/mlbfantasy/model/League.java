package com.mlbfantasy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "league")
public class League {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "league_name", nullable = false)
    private String leagueName;

    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    @Column(name = "commissioner_id")
    private UUID commissionerId;

    @Column(name = "salary_cap", nullable = false)
    private BigDecimal salaryCap = new BigDecimal("50000000.00");

    @Column(name = "roster_size", nullable = false)
    private int rosterSize = 10;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected League() {
    }

    public League(String leagueName, int seasonYear, UUID commissionerId) {
        this.leagueName = leagueName;
        this.seasonYear = seasonYear;
        this.commissionerId = commissionerId;
    }

    public UUID getId() {
        return id;
    }

    public String getLeagueName() {
        return leagueName;
    }

    public void setLeagueName(String leagueName) {
        this.leagueName = leagueName;
    }

    public int getSeasonYear() {
        return seasonYear;
    }

    public void setSeasonYear(int seasonYear) {
        this.seasonYear = seasonYear;
    }

    public UUID getCommissionerId() {
        return commissionerId;
    }

    public void setCommissionerId(UUID commissionerId) {
        this.commissionerId = commissionerId;
    }

    public BigDecimal getSalaryCap() {
        return salaryCap;
    }

    public void setSalaryCap(BigDecimal salaryCap) {
        this.salaryCap = salaryCap;
    }

    public int getRosterSize() {
        return rosterSize;
    }

    public void setRosterSize(int rosterSize) {
        this.rosterSize = rosterSize;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
