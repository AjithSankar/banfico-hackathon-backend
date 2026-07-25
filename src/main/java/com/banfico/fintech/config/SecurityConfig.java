package com.banfico.fintech.config;

import com.banfico.fintech.auth.SessionAuthFilter;
import com.banfico.fintech.common.RequestLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Authorization on /api/** is enforced by SessionAuthFilter, not Spring Security's
 * authorizeHttpRequests DSL — the filter is the source of truth for which session tokens are
 * valid (backed by SandboxTokenService's in-memory session cache). This config just wires it
 * in and disables CSRF/HTTP sessions since we're a stateless bearer-token API.
 *
 * RequestLoggingFilter is placed before SessionAuthFilter so it wraps the whole chain — every
 * request gets logged with its outcome, including 401s from SessionAuthFilter itself.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SessionAuthFilter sessionAuthFilter,
                                                     RequestLoggingFilter requestLoggingFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(sessionAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(requestLoggingFilter, SessionAuthFilter.class);
        return http.build();
    }
}
