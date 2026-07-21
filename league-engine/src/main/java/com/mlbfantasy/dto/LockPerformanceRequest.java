package com.mlbfantasy.dto;

/**
 * Lock a player's scoring game for the week. When {@code gamePk} is null, the
 * engine locks the current best eligible game.
 */
public record LockPerformanceRequest(Integer gamePk) {
}
