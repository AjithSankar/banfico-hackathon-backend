package com.banfico.fintech;

import com.banfico.fintech.config.SandboxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Excludes UserDetailsServiceAutoConfiguration: we never use Spring Security's UserDetailsService
 * or AuthenticationManager — SessionAuthFilter authenticates directly against SandboxTokenService
 * and writes the result straight into SecurityContextHolder. Without this exclusion, Boot spins
 * up an unused in-memory user with a random generated password on every startup.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(SandboxProperties.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
