package com.valor.exception;

import org.springframework.http.HttpStatus;

public class LockedAuthException extends AuthApiException {
    private final long remainingLockSeconds;

    public LockedAuthException(String message, HttpStatus status, long remainingLockSeconds) {
        super(message, status);
        this.remainingLockSeconds = remainingLockSeconds;
    }

    public long getRemainingLockSeconds() {
        return remainingLockSeconds;
    }
}
