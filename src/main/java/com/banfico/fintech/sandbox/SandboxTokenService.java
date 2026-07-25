package com.banfico.fintech.sandbox;

import com.banfico.fintech.common.exception.SandboxAuthException;
import com.banfico.fintech.config.SandboxProperties;
import com.banfico.fintech.sandbox.dto.TokenResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Exchanges end-user sandbox credentials for a Keycloak token bundle and caches it in-memory
 * keyed by our own session id. The raw access/refresh tokens never leave this service — callers
 * only ever get a valid access token to inject into a SandboxAisClient call.
 */
@Service
public class SandboxTokenService {

    private final RestClient sandboxAuthRestClient;
    private final SandboxProperties properties;
    private final Retry tokenRetry;
    private final CircuitBreaker tokenCircuitBreaker;
    private final ConcurrentHashMap<String, TokenBundle> sessions = new ConcurrentHashMap<>();

    public SandboxTokenService(@Qualifier("sandboxAuthRestClient") RestClient sandboxAuthRestClient,
                                SandboxProperties properties,
                                RetryRegistry retryRegistry,
                                CircuitBreakerRegistry circuitBreakerRegistry) {
        this.sandboxAuthRestClient = sandboxAuthRestClient;
        this.properties = properties;
        this.tokenRetry = retryRegistry.retry("sandboxToken");
        this.tokenCircuitBreaker = circuitBreakerRegistry.circuitBreaker("sandboxToken");
    }

    public TokenBundle login(String sessionId, String username, String password) {
        TokenBundle bundle = toBundle(exchangeToken(passwordGrantForm(username, password)));
        sessions.put(sessionId, bundle);
        return bundle;
    }

    /** Returns a valid access token for this session, transparently refreshing if it's expiring soon. */
    public String getAccessToken(String sessionId) {
        TokenBundle bundle = sessions.get(sessionId);
        if (bundle == null) {
            throw new SandboxAuthException("No active sandbox session for id: " + sessionId);
        }
        if (bundle.isAccessTokenExpiringSoon()) {
            bundle = refresh(sessionId);
        }
        return bundle.accessToken();
    }

    public TokenBundle refresh(String sessionId) {
        TokenBundle existing = sessions.get(sessionId);
        if (existing == null) {
            throw new SandboxAuthException("No active sandbox session for id: " + sessionId);
        }
        if (existing.isRefreshTokenExpired()) {
            sessions.remove(sessionId);
            throw new SandboxAuthException("Sandbox session expired, please log in again: " + sessionId);
        }
        TokenBundle bundle = toBundle(exchangeToken(refreshGrantForm(existing.refreshToken())));
        sessions.put(sessionId, bundle);
        return bundle;
    }

    public void invalidate(String sessionId) {
        sessions.remove(sessionId);
    }

    private TokenResponse exchangeToken(MultiValueMap<String, String> form) {
        Supplier<TokenResponse> call = () -> sandboxAuthRestClient.post()
                .uri("/auth/realms/{tenant}/protocol/openid-connect/token", properties.tenant())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(status -> status.value() == 400 || status.value() == 401, (req, res) -> {
                    throw new SandboxAuthException("Sandbox rejected the token request (status " + res.getStatusCode() + ")");
                })
                .body(TokenResponse.class);
        // Resilience4j applied programmatically (not via annotation) since this is a private,
        // self-invoked method — an @Retry/@CircuitBreaker annotation here would be silently
        // skipped by Spring AOP.
        Supplier<TokenResponse> decorated = CircuitBreaker.decorateSupplier(tokenCircuitBreaker,
                Retry.decorateSupplier(tokenRetry, call));
        return decorated.get();
    }

    private MultiValueMap<String, String> passwordGrantForm(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("username", username);
        form.add("password", password);
        form.add("grant_type", "password");
        return form;
    }

    private MultiValueMap<String, String> refreshGrantForm(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        return form;
    }

    private TokenBundle toBundle(TokenResponse response) {
        Instant now = Instant.now();
        return new TokenBundle(
                response.accessToken(),
                response.refreshToken(),
                now.plusSeconds(response.expiresIn()),
                now.plusSeconds(response.refreshExpiresIn()));
    }
}
