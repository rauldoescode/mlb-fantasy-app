package com.mlbfantasy.dto;

import java.util.Map;
import java.util.UUID;

/** Current scoring formula for a league (points per unit of each category). */
public record ScoringRulesResponse(
        UUID leagueId,
        Map<String, Double> pointValues) {
}
