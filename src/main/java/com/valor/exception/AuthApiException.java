package com.valor.exception;

import org.springframework.http.HttpStatus;

public class AuthApiException extends RuntimeException {

    private final HttpStatus status;

    public AuthApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}