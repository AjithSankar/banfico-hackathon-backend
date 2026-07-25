package com.banfico.fintech.account;

import java.math.BigDecimal;

/** Flattened account shape for our own API — used in GET /api/accounts and /api/dashboard. */
public record AccountSummary(
        String id,
        String nickname,
        String type,
        String currency,
        BigDecimal balance,
        String maskedIdentification) {
}
