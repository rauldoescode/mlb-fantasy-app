package com.mlbfantasy.dto;

import com.mlbfantasy.model.LeagueVisibility;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateLeagueRequest(
        @NotBlank String name,
        @NotNull Integer seasonYear,
        @NotBlank String teamName,
        LeagueVisibility visibility,
        Integer maxMembers,
        BigDecimal salaryCap,
        @Positive Integer rosterSize) {

    @AssertTrue(message = "maxMembers must be an even number between 2 and 12")
    public boolean isMaxMembersValid() {
        if (maxMembers == null) {
            return true;
        }
        return maxMembers >= 2 && maxMembers <= 12 && maxMembers % 2 == 0;
    }
}
