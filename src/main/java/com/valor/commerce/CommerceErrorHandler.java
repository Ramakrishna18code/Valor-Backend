package com.valor.commerce;

import com.valor.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.io.UncheckedIOException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackages = "com.valor.commerce")
class CommerceErrorHandler {
    @ExceptionHandler(CommerceException.class)
    ResponseEntity<ApiResponse<Void>> commerce(CommerceException ex) {
        return ResponseEntity.status(ex.status).body(ApiResponse.error(ex.getMessage(), ex.status));
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<ApiResponse<Void>> bad(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("Invalid request", 400));
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> denied() {
        return ResponseEntity.status(403).body(ApiResponse.error("Access denied", 403));
    }
    @ExceptionHandler(UncheckedIOException.class)
    ResponseEntity<ApiResponse<Void>> io() {
        return ResponseEntity.status(500).body(ApiResponse.error("Storage unavailable", 500));
    }
}
