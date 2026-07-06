package com.mlbfantasy.dto;

import com.mlbfantasy.model.Player;
import java.math.BigDecimal;

public record PlayerResponse(
        Integer mlbId,
        String fullName,
        String position,
        String teamAbbrev,
        String currentStatus,
        String jerseyNumber,
        Boolean active,
        BigDecimal salary) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(
                player.getMlbId(),
                player.getFullName(),
                player.getPrimaryPos(),
                player.getTeamAbbrev(),
                player.getCurrentStatus(),
                player.getJerseyNumber(),
                player.getActive(),
                player.getSalary());
    }
}
