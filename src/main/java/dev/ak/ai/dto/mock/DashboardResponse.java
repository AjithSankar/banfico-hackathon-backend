package dev.ak.ai.dto.mock;

import java.util.List;

public record DashboardResponse(
    String customerName, 
    double netWorth, 
    List<Account> accounts,
    List<Transaction> recentTransactions
) {}