package com.banfico.fintech.common;

import com.banfico.fintech.config.SandboxProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Shared RestClient beans for the sandbox integration layer, with connect/read timeouts so a
 * slow third-party sandbox call can't hang a request indefinitely.
 */
@Configuration
public class RestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @Bean
    public RestClient sandboxAuthRestClient(SandboxProperties properties) {
        return RestClient.builder()
                .baseUrl("https://auth." + properties.domain())
                .requestFactory(requestFactory())
                .build();
    }

    @Bean
    public RestClient sandboxAisRestClient(SandboxProperties properties) {
        return RestClient.builder()
                .baseUrl("https://core-api." + properties.domain() + "/api/obie-aisp/v4.0")
                .requestFactory(requestFactory())
                .build();
    }
}
