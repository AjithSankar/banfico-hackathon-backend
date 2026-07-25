package com.banfico.fintech.sandbox.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ObieBalanceData(List<ObieBalance> Balance) {
}
