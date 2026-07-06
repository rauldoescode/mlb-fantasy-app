package com.mlbfantasy.dto;

import com.mlbfantasy.model.League;
import java.math.BigDecimal;
import java.util.UUID;

public record LeagueResponse(
        UUID id,
        String name,
        int seasonYear,
        UUID commissionerId,
        BigDecimal salaryCap,
        int rosterSize) {

    public static LeagueResponse from(League league) {
        return new LeagueResponse(
                league.getId(),
                league.getLeagueName(),
                league.getSeasonYear(),
                league.getCommissionerId(),
                league.getSalaryCap(),
                league.getRosterSize());
    }
}
