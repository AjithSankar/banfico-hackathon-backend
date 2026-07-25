package com.banfico.fintech.sandbox.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Response envelope for GET/POST /accounts and GET /accounts/{id} — {"Data":{"Account":[...]}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ObieAccountsResponse(ObieAccountData Data) {
}
