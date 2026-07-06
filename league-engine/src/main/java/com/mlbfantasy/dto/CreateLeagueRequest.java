package com.mlbfantasy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateLeagueRequest(
        @NotBlank String name,
        @NotNull Integer seasonYear,
        @NotBlank String teamName,
        BigDecimal salaryCap,
        @Positive Integer rosterSize) {
}
