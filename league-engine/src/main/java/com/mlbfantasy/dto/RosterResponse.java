package com.mlbfantasy.dto;

import java.math.BigDecimal;
import java.util.List;

public record RosterResponse(
        List<RosterSlotResponse> slots,
        BigDecimal totalSalary,
        BigDecimal salaryCap,
        BigDecimal salaryRemaining) {
}
