package com.banfico.fintech.ai;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String message, String conversationId) {
}
