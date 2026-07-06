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

@Entity
@Table(name = "roster_slots")
public class RosterSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "league_id")
    private UUID leagueId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "player_id")
    private Integer playerId;

    @Column(name = "slot_type", nullable = false)
    private String slotType;

    @Column(name = "is_active")
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "assigned_at", updatable = false)
    private OffsetDateTime assignedAt;

    protected RosterSlot() {
    }

    public RosterSlot(UUID leagueId, UUID userId, Integer playerId, String slotType, boolean active) {
        this.leagueId = leagueId;
        this.userId = userId;
        this.playerId = playerId;
        this.slotType = slotType;
        this.active = active;
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

    public Integer getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Integer playerId) {
        this.playerId = playerId;
    }

    public String getSlotType() {
        return slotType;
    }

    public void setSlotType(String slotType) {
        this.slotType = slotType;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }
}
