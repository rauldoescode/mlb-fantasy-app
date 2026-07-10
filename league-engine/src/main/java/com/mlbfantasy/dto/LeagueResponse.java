package com.mlbfantasy.dto;

import com.mlbfantasy.model.League;
import com.mlbfantasy.model.LeagueVisibility;
import java.math.BigDecimal;
import java.util.UUID;

public record LeagueResponse(
        UUID id,
        String name,
        int seasonYear,
        UUID commissionerId,
        BigDecimal salaryCap,
        int rosterSize,
        LeagueVisibility visibility,
        long memberCount,
        int maxMembers,
        String joinCode) {

    /**
     * Builds a response, only exposing the join code when the requester is the
     * league commissioner. Pass {@code null} for {@code requesterId} to omit it.
     */
    public static LeagueResponse from(League league, UUID requesterId, long memberCount) {
        boolean isCommissioner = requesterId != null
                && requesterId.equals(league.getCommissionerId());
        return new LeagueResponse(
                league.getId(),
                league.getLeagueName(),
                league.getSeasonYear(),
                league.getCommissionerId(),
                league.getSalaryCap(),
                league.getRosterSize(),
                league.getVisibility(),
                memberCount,
                league.getMaxMembers(),
                isCommissioner ? league.getJoinCode() : null);
    }
}
