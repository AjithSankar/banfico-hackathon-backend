package com.banfico.fintech.sandbox.dto;

/** Used for CreditorAgent / DebtorAgent on a transaction create request. */
public record ObieAgent(String SchemeName, String Identification, ObiePostalAddress PostalAddress) {
}
