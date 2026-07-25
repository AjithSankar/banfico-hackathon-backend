package com.banfico.fintech.insights;

import java.math.BigDecimal;
import java.time.Instant;

/** A merchant with recurring, similarly-sized, roughly-monthly transactions. */
public record SubscriptionCandidate(
        String merchantName,
        BigDecimal averageAmount,
        String currency,
        int occurrenceCount,
        double estimatedFrequencyDays,
        Instant lastBookingDateTime) {
}
