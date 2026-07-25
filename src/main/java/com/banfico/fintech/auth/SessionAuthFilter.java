package com.banfico.fintech.auth;

import com.banfico.fintech.common.ApiResponse;
import com.banfico.fintech.common.exception.SandboxAuthException;
import com.banfico.fintech.sandbox.SandboxTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Validates our own session token (a bearer opaque id) on every /api/** request except
 * /api/auth/login, resolving it to the cached sandbox token bundle via SandboxTokenService.
 * Swagger/actuator paths aren't under /api/** so they're untouched by this filter.
 */
@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_API_PATHS = Set.of("/api/auth/login");
    private static final String BEARER_PREFIX = "Bearer ";

    private final SandboxTokenService tokenService;
    private final ObjectMapper objectMapper;

    public SessionAuthFilter(SandboxTokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || PUBLIC_API_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            unauthorized(response, "Missing bearer session token");
            return;
        }
        String sessionId = header.substring(BEARER_PREFIX.length()).trim();
        try {
            tokenService.getAccessToken(sessionId);
        } catch (SandboxAuthException ex) {
            unauthorized(response, ex.getMessage());
            return;
        }
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                sessionId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(message));
    }
}
