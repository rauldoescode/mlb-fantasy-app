package com.mlbfantasy.dto;

import java.util.UUID;

/**
 * Public-facing view of a joinable league. Never exposes the join code.
 */
public record PublicLeagueResponse(
        UUID id,
        String name,
        int seasonYear,
        String commissionerDisplayName,
        long memberCount,
        int maxMembers) {
}
