package com.banfico.fintech.insights;

import java.math.BigDecimal;

/**
 * A category whose spend increased significantly vs. the previous month (or is brand new this
 * month). {@code percentageIncrease} is null when {@code previousMonthAmount} was zero (a new
 * spending category rather than a percentage change). {@code severity} is "high" (>=50%
 * increase) or "medium" (>=20% increase, or a new category).
 */
public record OverspendingAlert(
        String category,
        BigDecimal currentMonthAmount,
        BigDecimal previousMonthAmount,
        BigDecimal percentageIncrease,
        String severity) {
}
