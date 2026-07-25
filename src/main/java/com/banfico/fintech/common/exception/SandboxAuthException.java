package com.banfico.fintech.common.exception;

/** Thrown when the sandbox rejects our credentials/token — maps to a clean 401 (Phase 7). */
public class SandboxAuthException extends RuntimeException {

    public SandboxAuthException(String message) {
        super(message);
    }

    public SandboxAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
