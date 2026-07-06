package com.mlbfantasy.dto;

public record UpdateRosterSlotRequest(
        Boolean active,
        String slotType) {
}
