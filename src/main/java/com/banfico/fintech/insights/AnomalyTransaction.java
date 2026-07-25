package com.banfico.fintech.insights;

import com.banfico.fintech.transaction.TransactionSummary;

import java.math.BigDecimal;

/** A transaction flagged as an outlier vs. its own category's mean + 2x standard deviation. */
public record AnomalyTransaction(
        TransactionSummary transaction,
        BigDecimal categoryMean,
        BigDecimal categoryStdDev,
        BigDecimal deviationMultiple) {
}
