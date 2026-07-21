package com.mlbfantasy.dto;

import java.util.List;

/** Full weekly scoring result: team category breakdown plus per-player lines. */
public record WeekScoreResult(
        ScoreBreakdown breakdown,
        List<PlayerWeekScore> playerScores) {
}
