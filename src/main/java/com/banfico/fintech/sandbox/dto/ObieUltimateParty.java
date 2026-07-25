package com.banfico.fintech.sandbox.dto;

/** Used for UltimateCreditor / UltimateDebtor on a transaction create request. */
public record ObieUltimateParty(String SchemeName, ObiePostalAddress PostalAddress) {
}
