package com.banfico.fintech.sandbox.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ObieStatementFrequency(
        String CommunicationMethod,
        String Format,
        String Frequency,
        ObieDeliveryAddress DeliveryAddress) {
}
