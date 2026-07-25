package dev.ak.ai.dto.mock;

public record Transaction(
    String id, 
    String accountId, 
    String date, 
    double amount, 
    String type, // CREDIT or DEBIT
    String merchant, 
    String category
) {}