package com.banfico.fintech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Backend-owned sandbox OAuth2 config (client id/secret/domain/tenant). End-user
 * username/password are never configured here — they come from the login request body.
 */
@ConfigurationProperties(prefix = "sandbox")
public record SandboxProperties(String domain, String tenant, String clientId, String clientSecret) {
}
