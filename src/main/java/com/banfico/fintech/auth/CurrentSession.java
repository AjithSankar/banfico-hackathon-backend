package com.banfico.fintech.auth;

import com.banfico.fintech.common.exception.SandboxAuthException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the sessionId that SessionAuthFilter placed in the SecurityContext for this request.
 * Used by controllers/services (Phase 4+) to thread the right session into SandboxAisClient
 * calls without passing it through every method signature.
 */
public final class CurrentSession {

    private CurrentSession() {
    }

    public static String sessionId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new SandboxAuthException("No authenticated session in context");
        }
        return authentication.getPrincipal().toString();
    }
}
