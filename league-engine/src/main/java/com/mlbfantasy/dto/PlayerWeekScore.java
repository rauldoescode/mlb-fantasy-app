package com.mlbfantasy.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Per-player weekly scoring contribution under best-game / performance-lock rules. */
public record PlayerWeekScore(
        Integer playerId,
        boolean slotActive,
        BigDecimal points,
        Integer scoringGamePk,
        boolean performanceLocked,
        Map<String, BigDecimal> categoryPoints,
        List<PlayerGamePerformance> games) {
}
