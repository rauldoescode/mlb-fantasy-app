package com.mlbfantasy.dto;

/**
 * Start/Bench toggle from the matchup lineup card. Only {@code active} is used;
 * slot type stays unchanged.
 */
public record SetMatchupLineupRequest(Boolean active) {
}
