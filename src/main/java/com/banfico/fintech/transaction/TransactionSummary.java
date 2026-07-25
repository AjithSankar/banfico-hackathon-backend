package com.banfico.fintech.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/** Flattened transaction shape for our own API, with the classified category attached. */
public record TransactionSummary(
        String id,
        String accountId,
        BigDecimal amount,
        String currency,
        String creditDebitIndicator,
        String status,
        Instant bookingDateTime,
        String description,
        String merchantName,
        String category) {
}
