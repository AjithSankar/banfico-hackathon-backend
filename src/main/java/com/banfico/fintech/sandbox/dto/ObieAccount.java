package com.banfico.fintech.sandbox.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Faithful shape of a single account resource as returned by GET /accounts, GET /accounts/{id}
 * and POST /accounts — confirmed live against the sandbox (2026-07-25). Note the sandbox's
 * top-level response has no Links/Meta envelope, just {@code {"Data":{"Account":[...]}}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ObieAccount(
        String AccountId,
        Boolean InternationalAccount,
        String Status,
        String StatusUpdateDateTime,
        String Currency,
        String AccountCategory,
        String AccountTypeCode,
        String Description,
        String Nickname,
        String OpeningDate,
        String MaturityDate,
        String SwitchStatus,
        List<ObieAccountIdentification> Account,
        ObieServicer Servicer,
        List<ObieStatementFrequency> StatementFrequencyAndFormat) {
}
