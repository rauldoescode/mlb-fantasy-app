package com.mlbfantasy.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** One player row on the matchup lineup card. */
public record LineupPlayerCard(
        UUID slotId,
        Integer playerId,
        String fullName,
        String position,
        String teamAbbrev,
        boolean active,
        boolean locked,
        BigDecimal weekPoints,
        BigDecimal bestGamePoints,
        Integer scoringGamePk,
        boolean performanceLocked,
        List<PlayerGamePerformance> games) {
}
