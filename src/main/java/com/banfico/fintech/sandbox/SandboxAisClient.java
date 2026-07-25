package com.banfico.fintech.sandbox;

import com.banfico.fintech.common.Masking;
import com.banfico.fintech.common.exception.SandboxAuthException;
import com.banfico.fintech.sandbox.dto.ObieAccountCreateRequest;
import com.banfico.fintech.sandbox.dto.ObieAccountsResponse;
import com.banfico.fintech.sandbox.dto.ObieBalancesResponse;
import com.banfico.fintech.sandbox.dto.ObieTransactionCreateRequest;
import com.banfico.fintech.sandbox.dto.ObieTransactionsResponse;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Typed wrapper over the OBIE AISP v4.0 endpoints. Every call is scoped to a session id: the
 * bearer token is resolved fresh from SandboxTokenService per call, and a 401 triggers exactly
 * one refresh-and-retry before surfacing a SandboxAuthException.
 */
@Slf4j
@Service
public class SandboxAisClient {

    private final RestClient sandboxAisRestClient;
    private final SandboxTokenService tokenService;
    private final Retry aisRetry;

    public SandboxAisClient(@Qualifier("sandboxAisRestClient") RestClient sandboxAisRestClient,
                             SandboxTokenService tokenService,
                             RetryRegistry retryRegistry) {
        this.sandboxAisRestClient = sandboxAisRestClient;
        this.tokenService = tokenService;
        this.aisRetry = retryRegistry.retry("sandboxAis");
    }

    public ObieAccountsResponse getAccounts(String sessionId) {
        return executeWithAuth(sessionId, "GET /accounts", token -> sandboxAisRestClient.get()
                .uri("/accounts?type=domestic")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ObieAccountsResponse.class));
    }

    public ObieAccountsResponse getAccount(String sessionId, String accountId) {
        return executeWithAuth(sessionId, "GET /accounts/" + accountId, token -> sandboxAisRestClient.get()
                .uri("/accounts/{accountId}", accountId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ObieAccountsResponse.class));
    }

    public ObieBalancesResponse getBalances(String sessionId, String accountId) {
        return executeWithAuth(sessionId, "GET /accounts/" + accountId + "/balances", token -> sandboxAisRestClient.get()
                .uri("/accounts/{accountId}/balances", accountId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ObieBalancesResponse.class));
    }

    public ObieTransactionsResponse getTransactions(String sessionId, String accountId) {
        return executeWithAuth(sessionId, "GET /accounts/" + accountId + "/transactions", token -> sandboxAisRestClient.get()
                .uri("/accounts/{accountId}/transactions", accountId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ObieTransactionsResponse.class));
    }

    /** Demo-seeding only (bonus) — not a core end-user-facing feature. */
    public ObieAccountsResponse createAccount(String sessionId, ObieAccountCreateRequest request) {
        return executeWithAuth(sessionId, "POST /accounts", token -> sandboxAisRestClient.post()
                .uri("/accounts")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ObieAccountsResponse.class));
    }

    /** Demo-seeding only (bonus) — not a core end-user-facing feature. */
    public ObieTransactionsResponse createTransaction(String sessionId, String accountId, ObieTransactionCreateRequest request) {
        return executeWithAuth(sessionId, "POST /accounts/" + accountId + "/transactions", token -> sandboxAisRestClient.post()
                .uri("/accounts/{accountId}/transactions", accountId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ObieTransactionsResponse.class));
    }

    private <T> T executeWithAuth(String sessionId, String operation, Function<String, T> call) {
        String maskedSession = Masking.truncate(sessionId);
        log.debug("Sandbox AIS call sessionId={} operation={}", maskedSession, operation);
        String token = tokenService.getAccessToken(sessionId);
        try {
            return withResilience(() -> call.apply(token));
        } catch (HttpClientErrorException.Unauthorized firstFailure) {
            log.warn("Sandbox rejected token sessionId={} operation={}, refreshing and retrying once", maskedSession, operation);
            String refreshedToken = tokenService.refresh(sessionId).accessToken();
            try {
                return withResilience(() -> call.apply(refreshedToken));
            } catch (HttpClientErrorException.Unauthorized secondFailure) {
                log.error("Sandbox rejected refreshed token sessionId={} operation={}", maskedSession, operation);
                throw new SandboxAuthException("Sandbox rejected the refreshed token for session " + sessionId, secondFailure);
            }
        }
    }

    private <T> T withResilience(Supplier<T> call) {
        // No circuit breaker: at hackathon-demo call volumes it only adds a self-inflicted outage
        // window after a transient blip trips it open (see IMPLEMENTATION_PLAN.md) — retry alone
        // is the right amount of resilience here.
        Supplier<T> decorated = Retry.decorateSupplier(aisRetry, call);
        return decorated.get();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
