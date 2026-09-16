package com.valor.tracking;

import com.valor.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackages = "com.valor.tracking")
class TrackingErrorHandler {
    @ExceptionHandler(TrackingException.class)
    ResponseEntity<ApiResponse<Void>> tracking(TrackingException ex) {
        return ResponseEntity.status(ex.status).body(ApiResponse.error(ex.getMessage(), ex.status));
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<ApiResponse<Void>> bad() {
        return ResponseEntity.badRequest().body(ApiResponse.error("Invalid request", 400));
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> denied() {
        return ResponseEntity.status(403).body(ApiResponse.error("Access denied", 403));
    }
}
