package com.mlbfantasy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Fantasy points for a single box score, used for best-game preview and locks. */
public record PlayerGamePerformance(
        Integer gamePk,
        LocalDate gameDate,
        BigDecimal points,
        Map<String, BigDecimal> categoryPoints,
        boolean eligible) {
}
