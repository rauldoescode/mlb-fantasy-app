package com.mlbfantasy.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** One side of a matchup: team identity plus starter/bench cards. */
public record MatchupLineupSide(
        UUID userId,
        String teamName,
        List<LineupPlayerCard> starters,
        List<LineupPlayerCard> bench,
        BigDecimal totalPoints) {
}
