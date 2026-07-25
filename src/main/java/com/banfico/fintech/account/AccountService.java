package com.banfico.fintech.account;

import com.banfico.fintech.common.Concurrency;
import com.banfico.fintech.sandbox.SandboxAisClient;
import com.banfico.fintech.sandbox.dto.ObieAccount;
import com.banfico.fintech.sandbox.dto.ObieAccountCreateRequest;
import com.banfico.fintech.sandbox.dto.ObieAccountIdentification;
import com.banfico.fintech.sandbox.dto.ObieBalance;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Maps raw OBIE account/balance DTOs (sandbox/dto) to our own flattened API shapes. The
 * sandbox has no "balance" field on the account resource itself, so listAccounts fans out a
 * balance call per account concurrently rather than making the frontend do N extra requests.
 */
@Service
public class AccountService {

    private final SandboxAisClient aisClient;

    public AccountService(SandboxAisClient aisClient) {
        this.aisClient = aisClient;
    }

    public List<AccountSummary> listAccounts(String sessionId) {
        List<ObieAccount> accounts = aisClient.getAccounts(sessionId).Data().Account();
        return Concurrency.mapConcurrently(accounts, account -> toSummary(sessionId, account));
    }

    public AccountDetail getAccount(String sessionId, String accountId) {
        ObieAccount account = firstAccount(aisClient.getAccount(sessionId, accountId).Data().Account());
        ObieBalance balance = firstBalanceOrNull(sessionId, accountId);
        return toDetail(account, balance);
    }

    public BalanceResponse getBalance(String sessionId, String accountId) {
        ObieBalance balance = firstBalanceOrNull(sessionId, accountId);
        if (balance == null) {
            return new BalanceResponse(accountId, null, null, null, null, null);
        }
        return toBalanceResponse(balance);
    }

    /** Demo-seeding only (bonus) — not a core end-user-facing feature. */
    public AccountSummary createAccount(String sessionId, ObieAccountCreateRequest request) {
        ObieAccount created = firstAccount(aisClient.createAccount(sessionId, request).Data().Account());
        return toSummary(created, null);
    }

    private AccountSummary toSummary(String sessionId, ObieAccount account) {
        return toSummary(account, firstBalanceOrNull(sessionId, account.AccountId()));
    }

    private AccountSummary toSummary(ObieAccount account, ObieBalance balance) {
        return new AccountSummary(
                account.AccountId(),
                account.Nickname(),
                account.AccountTypeCode(),
                account.Currency(),
                balance == null ? null : new BigDecimal(balance.Amount().Amount()),
                maskIdentification(account));
    }

    private AccountDetail toDetail(ObieAccount account, ObieBalance balance) {
        return new AccountDetail(
                account.AccountId(),
                account.Nickname(),
                account.AccountTypeCode(),
                account.AccountCategory(),
                account.Status(),
                account.Currency(),
                balance == null ? null : new BigDecimal(balance.Amount().Amount()),
                maskIdentification(account),
                account.OpeningDate(),
                account.Servicer() == null ? null : account.Servicer().Name());
    }

    private BalanceResponse toBalanceResponse(ObieBalance balance) {
        return new BalanceResponse(
                balance.AccountId(),
                new BigDecimal(balance.Amount().Amount()),
                balance.Amount().Currency(),
                balance.CreditDebitIndicator(),
                balance.Type(),
                balance.DateTime());
    }

    private ObieBalance firstBalanceOrNull(String sessionId, String accountId) {
        List<ObieBalance> balances = aisClient.getBalances(sessionId, accountId).Data().Balance();
        return balances == null || balances.isEmpty() ? null : balances.getFirst();
    }

    private ObieAccount firstAccount(List<ObieAccount> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            throw new IllegalStateException("Sandbox returned no account data");
        }
        return accounts.getFirst();
    }

    private String maskIdentification(ObieAccount account) {
        List<ObieAccountIdentification> identifications = account.Account();
        if (identifications == null || identifications.isEmpty()) {
            return null;
        }
        String identification = identifications.getFirst().Identification();
        if (identification == null || identification.length() <= 4) {
            return identification;
        }
        String last4 = identification.substring(identification.length() - 4);
        return "*".repeat(identification.length() - 4) + last4;
    }
}
