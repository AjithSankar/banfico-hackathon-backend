package com.banfico.fintech.insights;

import java.math.BigDecimal;

/** Income vs. expense for one calendar month, across all accounts. */
public record SpendingSummary(
        String month,
        String currency,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal netChange,
        int transactionCount) {
}
