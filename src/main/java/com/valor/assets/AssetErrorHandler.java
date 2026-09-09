package com.valor.assets;

import com.valor.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.valor.assets")
class AssetErrorHandler {
    @ExceptionHandler(AssetException.class)
    ResponseEntity<ApiResponse<Object>> asset(AssetException error) {
        return response(error.status(), error.getMessage());
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class, ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<Object>> invalid(Exception ignored) { return response(400, "Invalid asset request"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Object>> conflict(Exception ignored) { return response(409, "Asset conflicts with existing data"); }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiResponse<Object>> unauthenticated(Exception ignored) { return response(401, "Authentication required"); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Object>> forbidden(Exception ignored) { return response(403, "Access denied"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Object>> unexpected(Exception ignored) { return response(500, "Internal server error"); }

    private ResponseEntity<ApiResponse<Object>> response(int status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(message, status));
    }
}
