package com.mlbfantasy.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "league_members")
public class LeagueMember {

    @EmbeddedId
    private LeagueMemberId id;

    @Column(name = "team_name", nullable = false)
    private String teamName;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private OffsetDateTime joinedAt;

    protected LeagueMember() {
    }

    public LeagueMember(UUID leagueId, UUID userId, String teamName) {
        this.id = new LeagueMemberId(leagueId, userId);
        this.teamName = teamName;
    }

    public LeagueMemberId getId() {
        return id;
    }

    public UUID getLeagueId() {
        return id.getLeagueId();
    }

    public UUID getUserId() {
        return id.getUserId();
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }
}
