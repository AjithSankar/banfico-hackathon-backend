package com.banfico.fintech.auth;

import jakarta.validation.constraints.NotBlank;

/** End customer's bank sandbox credentials, submitted from our app's login form. */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}
