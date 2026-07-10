package com.mlbfantasy.dto;

import com.mlbfantasy.model.LeagueVisibility;
import jakarta.validation.constraints.NotNull;

public record UpdateVisibilityRequest(
        @NotNull LeagueVisibility visibility) {
}
