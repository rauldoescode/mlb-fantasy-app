package com.mlbfantasy.dto;

import com.mlbfantasy.model.Matchup;
import java.math.BigDecimal;
import java.util.UUID;

public record MatchupResponse(
        UUID id,
        UUID leagueId,
        int weekNumber,
        UUID userOneId,
        BigDecimal userOneScore,
        UUID userTwoId,
        BigDecimal userTwoScore,
        UUID winnerId,
        String status) {

    public static MatchupResponse from(Matchup m) {
        return new MatchupResponse(
                m.getId(),
                m.getLeagueId(),
                m.getWeekNumber(),
                m.getUserOneId(),
                m.getUserOneScore(),
                m.getUserTwoId(),
                m.getUserTwoScore(),
                m.getWinnerId(),
                m.getStatus());
    }
}
