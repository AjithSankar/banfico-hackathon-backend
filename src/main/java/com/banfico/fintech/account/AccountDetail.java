package com.banfico.fintech.account;

import java.math.BigDecimal;

/** Single-account detail for GET /api/accounts/{id} — AccountSummary plus a few extra fields. */
public record AccountDetail(
        String id,
        String nickname,
        String type,
        String accountCategory,
        String status,
        String currency,
        BigDecimal balance,
        String maskedIdentification,
        String openingDate,
        String servicerName) {
}
