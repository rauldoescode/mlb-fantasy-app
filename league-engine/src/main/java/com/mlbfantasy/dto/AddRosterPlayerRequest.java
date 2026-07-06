package com.mlbfantasy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddRosterPlayerRequest(
        @NotNull Integer playerId,
        @NotBlank String slotType,
        Boolean active) {
}
