package com.banfico.fintech.sandbox.dto;

import java.util.List;

/** Request body for POST /accounts — exact shape from the Postman collection (demo-seeding, bonus). */
public record ObieAccountCreateRequest(
        String Nickname,
        String StatusUpdateDateTime,
        String OpeningDate,
        String Status,
        String AccountCategory,
        String AccountTypeCode,
        String Balance,
        String Currency,
        List<ObieAccountIdentification> Account,
        ObieServicer Servicer,
        List<ObieStatementFrequency> StatementFrequencyAndFormat,
        Boolean InternationalAccount) {
}
