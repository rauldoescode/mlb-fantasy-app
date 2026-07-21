package com.mlbfantasy.dto;

import java.time.LocalDate;

public record MatchupDetailResponse(
        MatchupResponse matchup,
        LocalDate weekStart,
        LocalDate weekEnd,
        ScoreBreakdown userOneBreakdown,
        ScoreBreakdown userTwoBreakdown,
        MatchupLineupSide userOneLineup,
        MatchupLineupSide userTwoLineup,
        /** True when the requester may Start/Bench their own players on this matchup. */
        boolean lineupEditable) {
}
