package com.banfico.fintech.sandbox;

import java.time.Instant;

/**
 * Sandbox OAuth2 token bundle cached in-memory keyed by our own session id. Never returned
 * to the frontend directly — only used internally to authenticate calls to SandboxAisClient.
 */
public record TokenBundle(
        String accessToken,
        String refreshToken,
        Instant accessExpiresAt,
        Instant refreshExpiresAt) {

    public boolean isAccessTokenExpiringSoon() {
        return Instant.now().plusSeconds(30).isAfter(accessExpiresAt);
    }

    public boolean isRefreshTokenExpired() {
        return Instant.now().isAfter(refreshExpiresAt);
    }
}
