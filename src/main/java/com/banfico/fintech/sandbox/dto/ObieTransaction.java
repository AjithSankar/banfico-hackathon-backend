package com.banfico.fintech.sandbox.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * OBIE Transaction resource shape — confirmed live against the sandbox (2026-07-25) via
 * GET /accounts/{id}/transactions (AccountId/TransactionId returned in addition to the
 * POST /accounts/{id}/transactions request fields).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ObieTransaction(
        String AccountId,
        String TransactionId,
        String TransactionReference,
        ObieAmount Amount,
        String CreditDebitIndicator,
        String Status,
        String BookingDateTime,
        String ValueDateTime,
        String TransactionInformation,
        ObieBankTransactionCode BankTransactionCode,
        ObieMerchantDetails MerchantDetails,
        ObieTransactionBalance Balance) {
}
