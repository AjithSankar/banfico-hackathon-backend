package com.banfico.fintech.dashboard;

import com.banfico.fintech.account.AccountService;
import com.banfico.fintech.account.AccountSummary;
import com.banfico.fintech.common.Concurrency;
import com.banfico.fintech.transaction.TransactionService;
import com.banfico.fintech.transaction.TransactionSummary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Aggregates all accounts + balances + recent transactions into one response for the frontend
 * home screen. Every per-account sandbox call is fanned out concurrently via virtual threads
 * (see Concurrency) rather than looped sequentially.
 */
@Service
public class DashboardService {

    private static final int RECENT_TRANSACTIONS_LIMIT = 10;

    private final AccountService accountService;
    private final TransactionService transactionService;

    public DashboardService(AccountService accountService, TransactionService transactionService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    public DashboardResponse buildDashboard(String sessionId) {
        List<AccountSummary> accounts = accountService.listAccounts(sessionId);

        List<TransactionSummary> recentTransactions = Concurrency.mapConcurrently(accounts,
                        account -> transactionService.listTransactions(sessionId, account.id(), null, null, null))
                .stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(TransactionSummary::bookingDateTime).reversed())
                .limit(RECENT_TRANSACTIONS_LIMIT)
                .toList();

        BigDecimal totalBalance = accounts.stream()
                .map(AccountSummary::balance)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String currency = accounts.isEmpty() ? null : accounts.getFirst().currency();

        return new DashboardResponse(accounts, accounts.size(), totalBalance, currency, recentTransactions);
    }
}
