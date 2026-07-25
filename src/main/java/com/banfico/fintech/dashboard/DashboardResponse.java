package com.banfico.fintech.dashboard;

import com.banfico.fintech.account.AccountSummary;
import com.banfico.fintech.transaction.TransactionSummary;

import java.math.BigDecimal;
import java.util.List;

/** One aggregated shape for the frontend home screen. */
public record DashboardResponse(
        List<AccountSummary> accounts,
        int accountCount,
        BigDecimal totalBalance,
        String currency,
        List<TransactionSummary> recentTransactions) {
}
