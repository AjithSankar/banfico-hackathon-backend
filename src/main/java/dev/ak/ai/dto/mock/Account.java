package dev.ak.ai.dto.mock;

public record Account(
        String id,
        String type,
        String accountNumber,
        double balance,
        String currency
) {
}
