package com.banfico.fintech.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Logs method/path/status/duration for every request and stamps a short correlation id into
 * MDC (key "requestId") so every log line emitted while handling this request — including from
 * SandboxTokenService/SandboxAisClient deep in the call — can be grepped together. Wired as the
 * very first filter in the security chain (see SecurityConfig) so it wraps auth failures too.
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        MDC.put(REQUEST_ID_MDC_KEY, UUID.randomUUID().toString().substring(0, 8));
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        long start = System.currentTimeMillis();
        log.info(">> {} {}", method, query == null ? uri : uri + "?" + query);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            log.info("<< {} {} -> {} ({} ms)", method, uri, response.getStatus(), durationMs);
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }
}
