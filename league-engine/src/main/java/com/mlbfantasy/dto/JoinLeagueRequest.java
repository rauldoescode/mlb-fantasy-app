package com.mlbfantasy.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinLeagueRequest(
        @NotBlank String teamName) {
}
