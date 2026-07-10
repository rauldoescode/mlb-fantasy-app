package com.mlbfantasy.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinByCodeRequest(
        @NotBlank String joinCode,
        @NotBlank String teamName) {
}
