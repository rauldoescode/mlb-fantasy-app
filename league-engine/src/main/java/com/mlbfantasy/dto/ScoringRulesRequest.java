package com.mlbfantasy.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

public record ScoringRulesRequest(
        @NotEmpty Map<String, Double> pointValues) {
}
