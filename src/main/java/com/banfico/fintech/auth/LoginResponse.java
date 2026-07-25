package com.banfico.fintech.auth;

/** Our own opaque session token — never the raw sandbox access/refresh token. */
public record LoginResponse(String sessionToken, long expiresInSeconds) {
}
