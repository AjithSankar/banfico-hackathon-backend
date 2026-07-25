package com.banfico.fintech.sandbox.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * OBIE Balance resource shape — confirmed live against the sandbox (2026-07-25) via
 * GET /accounts/{id}/balances.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ObieBalance(
        String AccountId,
        String CreditDebitIndicator,
        String Type,
        String DateTime,
        ObieAmount Amount) {
}
