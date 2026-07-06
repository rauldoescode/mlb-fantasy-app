package com.mlbfantasy.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * The weekly scoring result for one user: total points plus a per-stat-category
 * breakdown (e.g. {"home_runs": 16.0, "rbi": 9.0}).
 */
public record ScoreBreakdown(
        UUID userId,
        BigDecimal totalPoints,
        Map<String, BigDecimal> categoryPoints) {
}
