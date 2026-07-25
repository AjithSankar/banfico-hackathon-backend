package com.banfico.fintech.sandbox;

import com.banfico.fintech.common.exception.SandboxAuthException;
import com.banfico.fintech.sandbox.dto.ObieAccountCreateRequest;
import com.banfico.fintech.sandbox.dto.ObieAccountsResponse;
import com.banfico.fintech.sandbox.dto.ObieBalancesResponse;
import com.banfico.fintech.sandbox.dto.ObieTransactionCreateRequest;
import com.banfico.fintech.sandbox.dto.ObieTransactionsResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
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
@Service
public class SandboxAisClient {

    private final RestClient sandboxAisRestClient;
    private final SandboxTokenService tokenService;
    private final Retry aisRetry;
    private final CircuitBreaker aisCircuitBreaker;

    public SandboxAisClient(@Qualifier("sandboxAisRestClient") RestClient sandboxAisRestClient,
                             SandboxTokenService tokenService,
                             RetryRegistry retryRegistry,
                             CircuitBreakerRegistry circuitBreakerRegistry) {
        this.sandboxAisRestClient = sandboxAisRestClient;
        this.tokenService = tokenService;
        this.aisRetry = retryRegistry.retry("sandboxAis");
        this.aisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("sandboxAis");
    }

    public ObieAccountsResponse getAccounts(String sessionId) {
        return executeWithAuth(sessionId, token -> sandboxAisRestClient.get()
                .uri("/accounts?type=domestic")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ObieAccountsResponse.class));
    }

    public ObieAccountsResponse getAccount(String sessionId, String accountId) {
        return executeWithAuth(sessionId, token -> sandboxAisRestClient.get()
                .uri("/accounts/{accountId}", accountId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ObieAccountsResponse.class));
    }

    public ObieBalancesResponse getBalances(String sessionId, String accountId) {
        return executeWithAuth(sessionId, token -> sandboxAisRestClient.get()
                .uri("/accounts/{accountId}/balances", accountId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ObieBalancesResponse.class));
    }

    public ObieTransactionsResponse getTransactions(String sessionId, String accountId) {
        return executeWithAuth(sessionId, token -> sandboxAisRestClient.get()
                .uri("/accounts/{accountId}/transactions", accountId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ObieTransactionsResponse.class));
    }

    /** Demo-seeding only (bonus) — not a core end-user-facing feature. */
    public ObieAccountsResponse createAccount(String sessionId, ObieAccountCreateRequest request) {
        return executeWithAuth(sessionId, token -> sandboxAisRestClient.post()
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
        return executeWithAuth(sessionId, token -> sandboxAisRestClient.post()
                .uri("/accounts/{accountId}/transactions", accountId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ObieTransactionsResponse.class));
    }

    private <T> T executeWithAuth(String sessionId, Function<String, T> call) {
        String token = tokenService.getAccessToken(sessionId);
        try {
            return withResilience(() -> call.apply(token));
        } catch (HttpClientErrorException.Unauthorized firstFailure) {
            String refreshedToken = tokenService.refresh(sessionId).accessToken();
            try {
                return withResilience(() -> call.apply(refreshedToken));
            } catch (HttpClientErrorException.Unauthorized secondFailure) {
                throw new SandboxAuthException("Sandbox rejected the refreshed token for session " + sessionId, secondFailure);
            }
        }
    }

    private <T> T withResilience(Supplier<T> call) {
        Supplier<T> decorated = CircuitBreaker.decorateSupplier(aisCircuitBreaker, Retry.decorateSupplier(aisRetry, call));
        return decorated.get();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
