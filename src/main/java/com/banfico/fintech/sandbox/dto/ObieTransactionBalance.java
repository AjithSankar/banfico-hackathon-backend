package com.banfico.fintech.sandbox.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ObieTransactionBalance(ObieAmount Amount, String CreditDebitIndicator, String Type) {
}
