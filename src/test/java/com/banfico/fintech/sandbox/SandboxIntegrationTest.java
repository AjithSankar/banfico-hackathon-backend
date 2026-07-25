package com.banfico.fintech.sandbox;

import com.banfico.fintech.sandbox.dto.ObieAccount;
import com.banfico.fintech.sandbox.dto.ObieAccountsResponse;
import com.banfico.fintech.sandbox.dto.ObieBalancesResponse;
import com.banfico.fintech.sandbox.dto.ObieTransactionsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 exit criteria: proves SandboxTokenService + SandboxAisClient work end-to-end against
 * the REAL Hackathon Mock Bank sandbox — not mocked. Only runs when SANDBOX_TEST_USERNAME /
 * SANDBOX_TEST_PASSWORD are set (see .env.example), so it's skipped by default for anyone
 * without sandbox test credentials and never breaks a plain `mvnw test` run.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SANDBOX_TEST_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SANDBOX_TEST_PASSWORD", matches = ".+")
class SandboxIntegrationTest {

    @Autowired
    private SandboxTokenService tokenService;

    @Autowired
    private SandboxAisClient aisClient;

    @Test
    void loginThenFetchAccountsBalancesAndTransactions_live() {
        String sessionId = UUID.randomUUID().toString();
        String username = System.getenv("SANDBOX_TEST_USERNAME");
        String password = System.getenv("SANDBOX_TEST_PASSWORD");

        TokenBundle bundle = tokenService.login(sessionId, username, password);
        assertThat(bundle.accessToken()).isNotBlank();

        ObieAccountsResponse accounts = aisClient.getAccounts(sessionId);
        List<ObieAccount> accountList = accounts.Data().Account();
        assertThat(accountList).isNotEmpty();
        System.out.println("[Phase 2 verify] accounts found: " + accountList.size());

        String firstAccountId = accountList.getFirst().AccountId();

        ObieBalancesResponse balances = aisClient.getBalances(sessionId, firstAccountId);
        System.out.println("[Phase 2 verify] balances response: " + balances);

        ObieTransactionsResponse transactions = aisClient.getTransactions(sessionId, firstAccountId);
        System.out.println("[Phase 2 verify] transactions response: " + transactions);

        tokenService.invalidate(sessionId);
    }
}
