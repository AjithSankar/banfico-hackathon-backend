package com.banfico.fintech.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Exposed as a CorsConfigurationSource and wired into HttpSecurity.cors(...) in SecurityConfig
 * — NOT as a plain WebMvcConfigurer. Spring Security installs its own CorsFilter as the very
 * first filter in the chain when configured this way, so CORS preflight (OPTIONS) requests are
 * answered before SessionAuthFilter ever sees them. A WebMvcConfigurer-only registration runs
 * too late: SessionAuthFilter would 401 every preflight request (they never carry credentials),
 * which browsers surface as a CORS error even though the real cause is our own auth filter.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
