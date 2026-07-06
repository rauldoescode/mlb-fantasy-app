package com.mlbfantasy.dto;

import com.mlbfantasy.model.Player;
import com.mlbfantasy.model.RosterSlot;
import java.math.BigDecimal;
import java.util.UUID;

public record RosterSlotResponse(
        UUID slotId,
        Integer playerId,
        String playerName,
        String position,
        String teamAbbrev,
        BigDecimal salary,
        String slotType,
        boolean active,
        boolean locked) {

    public static RosterSlotResponse from(RosterSlot slot, Player player, boolean locked) {
        return new RosterSlotResponse(
                slot.getId(),
                slot.getPlayerId(),
                player != null ? player.getFullName() : null,
                player != null ? player.getPrimaryPos() : null,
                player != null ? player.getTeamAbbrev() : null,
                player != null ? player.getSalary() : null,
                slot.getSlotType(),
                Boolean.TRUE.equals(slot.getActive()),
                locked);
    }
}
