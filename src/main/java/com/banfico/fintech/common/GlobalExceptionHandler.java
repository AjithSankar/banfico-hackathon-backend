package com.banfico.fintech.common;

import com.banfico.fintech.common.exception.SandboxAuthException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Minimal cross-cutting error mapping so Phase 3+ controllers have a consistent error shape
 * to build against. Full polish (sandbox 5xx/circuit-breaker mapping, etc.) is Phase 7.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SandboxAuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleSandboxAuth(SandboxAuthException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Unexpected error: " + ex.getMessage()));
    }
}
