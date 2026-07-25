package com.banfico.fintech.auth;

import com.banfico.fintech.common.ApiResponse;
import com.banfico.fintech.common.Masking;
import com.banfico.fintech.sandbox.SandboxTokenService;
import com.banfico.fintech.sandbox.TokenBundle;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private final SandboxTokenService tokenService;

    public LoginController(SandboxTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String sessionId = UUID.randomUUID().toString();
        log.info("Login attempt username={} sessionId={}", request.username(), Masking.truncate(sessionId));
        TokenBundle bundle = tokenService.login(sessionId, request.username(), request.password());
        long expiresInSeconds = Math.max(0, Duration.between(Instant.now(), bundle.accessExpiresAt()).getSeconds());
        log.info("Login succeeded username={} sessionId={}", request.username(), Masking.truncate(sessionId));
        return ApiResponse.ok(new LoginResponse(sessionId, expiresInSeconds));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        String sessionId = CurrentSession.sessionId();
        log.info("Logout sessionId={}", Masking.truncate(sessionId));
        tokenService.invalidate(sessionId);
        return ApiResponse.ok(null);
    }
}
