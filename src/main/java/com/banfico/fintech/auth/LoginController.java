package com.banfico.fintech.auth;

import com.banfico.fintech.common.ApiResponse;
import com.banfico.fintech.sandbox.SandboxTokenService;
import com.banfico.fintech.sandbox.TokenBundle;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

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
        TokenBundle bundle = tokenService.login(sessionId, request.username(), request.password());
        long expiresInSeconds = Math.max(0, Duration.between(Instant.now(), bundle.accessExpiresAt()).getSeconds());
        return ApiResponse.ok(new LoginResponse(sessionId, expiresInSeconds));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        tokenService.invalidate(CurrentSession.sessionId());
        return ApiResponse.ok(null);
    }
}
