package com.mlbfantasy.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Partial update for commissioner league settings. Only non-null fields are applied.
 */
public record UpdateLeagueSettingsRequest(
        String name,
        @DecimalMin(value = "0.01", message = "salaryCap must be greater than 0")
        BigDecimal salaryCap,
        @Positive Integer rosterSize,
        Integer maxMembers) {

    private static final int MIN_ROSTER_SIZE = 5;
    private static final int MAX_ROSTER_SIZE = 15;

    @AssertTrue(message = "At least one setting must be provided")
    public boolean isAnyFieldPresent() {
        return name != null || salaryCap != null || rosterSize != null || maxMembers != null;
    }

    @AssertTrue(message = "maxMembers must be an even number between 2 and 12")
    public boolean isMaxMembersValid() {
        if (maxMembers == null) {
            return true;
        }
        return maxMembers >= 2 && maxMembers <= 12 && maxMembers % 2 == 0;
    }

    @AssertTrue(message = "rosterSize must be between 5 and 15")
    public boolean isRosterSizeValid() {
        if (rosterSize == null) {
            return true;
        }
        return rosterSize >= MIN_ROSTER_SIZE && rosterSize <= MAX_ROSTER_SIZE;
    }
}
