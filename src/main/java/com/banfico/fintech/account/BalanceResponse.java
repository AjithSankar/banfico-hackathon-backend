package com.banfico.fintech.account;

import java.math.BigDecimal;

/** Flattened balance shape for GET /api/accounts/{id}/balance. */
public record BalanceResponse(
        String accountId,
        BigDecimal amount,
        String currency,
        String creditDebitIndicator,
        String type,
        String asOf) {
}
