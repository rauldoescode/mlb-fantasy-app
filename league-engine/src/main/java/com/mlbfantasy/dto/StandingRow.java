package com.mlbfantasy.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StandingRow(
        UUID userId,
        String displayName,
        String teamName,
        int wins,
        int losses,
        int ties,
        BigDecimal pointsFor) {
}
