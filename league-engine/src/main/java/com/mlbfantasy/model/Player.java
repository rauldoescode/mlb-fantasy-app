package com.mlbfantasy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @Column(name = "mlb_id")
    private Integer mlbId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "team_abbrev")
    private String teamAbbrev;

    @Column(name = "primary_pos")
    private String primaryPos;

    @Column(name = "is_active")
    private Boolean active;

    @Column(name = "current_status")
    private String currentStatus;

    @Column(name = "jersey_number")
    private String jerseyNumber;

    @Column(name = "salary")
    private BigDecimal salary;

    protected Player() {
    }

    public Integer getMlbId() {
        return mlbId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getTeamAbbrev() {
        return teamAbbrev;
    }

    public String getPrimaryPos() {
        return primaryPos;
    }

    public Boolean getActive() {
        return active;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public String getJerseyNumber() {
        return jerseyNumber;
    }

    public BigDecimal getSalary() {
        return salary;
    }

    public void setSalary(BigDecimal salary) {
        this.salary = salary;
    }
}
