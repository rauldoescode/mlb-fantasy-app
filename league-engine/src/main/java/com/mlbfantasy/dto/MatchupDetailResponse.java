package com.mlbfantasy.dto;

import java.time.LocalDate;

public record MatchupDetailResponse(
        MatchupResponse matchup,
        LocalDate weekStart,
        LocalDate weekEnd,
        ScoreBreakdown userOneBreakdown,
        ScoreBreakdown userTwoBreakdown) {
}
