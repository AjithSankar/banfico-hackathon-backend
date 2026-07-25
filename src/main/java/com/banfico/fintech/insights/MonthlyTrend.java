package com.banfico.fintech.insights;

import java.math.BigDecimal;

public record MonthlyTrend(
        String month,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal netChange,
        int transactionCount) {
}
