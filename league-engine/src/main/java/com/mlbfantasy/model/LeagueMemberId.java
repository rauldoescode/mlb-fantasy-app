package com.mlbfantasy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class LeagueMemberId implements Serializable {

    @Column(name = "league_id")
    private UUID leagueId;

    @Column(name = "user_id")
    private UUID userId;

    protected LeagueMemberId() {
    }

    public LeagueMemberId(UUID leagueId, UUID userId) {
        this.leagueId = leagueId;
        this.userId = userId;
    }

    public UUID getLeagueId() {
        return leagueId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LeagueMemberId that)) {
            return false;
        }
        return Objects.equals(leagueId, that.leagueId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(leagueId, userId);
    }
}
