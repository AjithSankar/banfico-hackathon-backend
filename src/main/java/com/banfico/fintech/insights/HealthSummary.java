package com.banfico.fintech.insights;

import java.math.BigDecimal;
import java.util.List;

/** Rule-based financial health snapshot. Phase 6 layers AI-generated coaching on top of this. */
public record HealthSummary(
        BigDecimal totalBalance,
        String currency,
        BigDecimal currentMonthIncome,
        BigDecimal currentMonthExpense,
        BigDecimal netChange,
        BigDecimal savingsRatePercent,
        String topCategory,
        List<String> observations) {
}
