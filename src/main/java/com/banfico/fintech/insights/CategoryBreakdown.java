package com.banfico.fintech.insights;

import java.math.BigDecimal;

public record CategoryBreakdown(
        String category,
        BigDecimal totalAmount,
        int transactionCount,
        BigDecimal percentageOfTotal) {
}
