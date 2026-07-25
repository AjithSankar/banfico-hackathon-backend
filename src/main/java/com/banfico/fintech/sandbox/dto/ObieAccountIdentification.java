package com.banfico.fintech.sandbox.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ObieAccountIdentification(
        String SchemeName,
        String Identification,
        String Name,
        String SecondaryIdentification,
        String LEI) {
}
