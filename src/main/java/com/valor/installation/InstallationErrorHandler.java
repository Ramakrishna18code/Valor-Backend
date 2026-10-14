package com.valor.installation;

import com.valor.response.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackages = "com.valor.installation")
class InstallationErrorHandler {
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Object>> denied(AccessDeniedException ignored) { return response(403, "Access denied"); }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiResponse<Object>> conflict(IllegalStateException error) { return response(409, error.getMessage()); }
    @ExceptionHandler({IllegalArgumentException.class, DataIntegrityViolationException.class})
    ResponseEntity<ApiResponse<Object>> invalid(Exception ignored) { return response(400, "Invalid installation request"); }
    private ResponseEntity<ApiResponse<Object>> response(int status, String message) { return ResponseEntity.status(status).body(ApiResponse.error(message, status)); }
}
