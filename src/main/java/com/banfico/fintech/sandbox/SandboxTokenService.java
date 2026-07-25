package com.banfico.fintech.sandbox;

import com.banfico.fintech.common.Masking;
import com.banfico.fintech.common.exception.SandboxAuthException;
import com.banfico.fintech.config.SandboxProperties;
import com.banfico.fintech.sandbox.dto.TokenResponse;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
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
 * only ever get a valid access token to inject into a SandboxAisClient call. Never logged, even
 * truncated — only the session id (itself a bearer credential for our API) is logged, truncated.
 */
@Slf4j
@Service
public class SandboxTokenService {

    private final RestClient sandboxAuthRestClient;
    private final SandboxProperties properties;
    private final Retry tokenRetry;
    private final ConcurrentHashMap<String, TokenBundle> sessions = new ConcurrentHashMap<>();

    public SandboxTokenService(@Qualifier("sandboxAuthRestClient") RestClient sandboxAuthRestClient,
                                SandboxProperties properties,
                                RetryRegistry retryRegistry) {
        this.sandboxAuthRestClient = sandboxAuthRestClient;
        this.properties = properties;
        this.tokenRetry = retryRegistry.retry("sandboxToken");
    }

    public TokenBundle login(String sessionId, String username, String password) {
        log.debug("Exchanging sandbox password grant sessionId={}", Masking.truncate(sessionId));
        TokenBundle bundle = toBundle(exchangeToken(passwordGrantForm(username, password)));
        sessions.put(sessionId, bundle);
        log.info("Sandbox session established sessionId={}", Masking.truncate(sessionId));
        return bundle;
    }

    /** Returns a valid access token for this session, transparently refreshing if it's expiring soon. */
    public String getAccessToken(String sessionId) {
        TokenBundle bundle = sessions.get(sessionId);
        if (bundle == null) {
            log.warn("No active sandbox session sessionId={}", Masking.truncate(sessionId));
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
            log.warn("Refresh requested for unknown sessionId={}", Masking.truncate(sessionId));
            throw new SandboxAuthException("No active sandbox session for id: " + sessionId);
        }
        if (existing.isRefreshTokenExpired()) {
            sessions.remove(sessionId);
            log.info("Sandbox refresh token expired, session dropped sessionId={}", Masking.truncate(sessionId));
            throw new SandboxAuthException("Sandbox session expired, please log in again: " + sessionId);
        }
        log.debug("Refreshing sandbox access token sessionId={}", Masking.truncate(sessionId));
        TokenBundle bundle = toBundle(exchangeToken(refreshGrantForm(existing.refreshToken())));
        sessions.put(sessionId, bundle);
        return bundle;
    }

    public void invalidate(String sessionId) {
        log.info("Sandbox session invalidated sessionId={}", Masking.truncate(sessionId));
        sessions.remove(sessionId);
    }

    private TokenResponse exchangeToken(MultiValueMap<String, String> form) {
        Supplier<TokenResponse> call = () -> sandboxAuthRestClient.post()
                .uri("/auth/realms/{tenant}/protocol/openid-connect/token", properties.tenant())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(status -> status.value() == 400 || status.value() == 401, (req, res) -> {
                    log.warn("Sandbox rejected token request status={}", res.getStatusCode());
                    throw new SandboxAuthException("Sandbox rejected the token request (status " + res.getStatusCode() + ")");
                })
                .body(TokenResponse.class);
        // Resilience4j applied programmatically (not via annotation) since this is a private,
        // self-invoked method — an @Retry annotation here would be silently skipped by Spring AOP.
        // No circuit breaker: at hackathon-demo call volumes, a breaker only adds a self-inflicted
        // outage window after a transient blip trips it (see IMPLEMENTATION_PLAN.md) — retry alone
        // is the right amount of resilience here.
        Supplier<TokenResponse> decorated = Retry.decorateSupplier(tokenRetry, call);
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
