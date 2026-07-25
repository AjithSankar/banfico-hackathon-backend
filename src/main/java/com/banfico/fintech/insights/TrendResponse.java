package com.banfico.fintech.insights;

import java.util.List;

/** Oldest-first, one entry per month in the requested window (zero-filled if no transactions that month). */
public record TrendResponse(String currency, List<MonthlyTrend> months) {
}
