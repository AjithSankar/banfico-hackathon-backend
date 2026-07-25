package com.banfico.fintech.sandbox.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ObieDeliveryAddress(
        String AddressType,
        String BuildingNumber,
        String StreetName,
        String TownName,
        String PostCode,
        String Country,
        String Floor) {
}
