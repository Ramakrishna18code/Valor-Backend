package com.valor.auth;

import com.valor.response.ApiResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.valor.auth")
class AuthErrorHandler {
  @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
  ResponseEntity<ApiResponse<Object>> authentication(Exception ignored) {
    return ResponseEntity.status(401).body(ApiResponse.error("Authentication required", 401));
  }
  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  ResponseEntity<ApiResponse<Object>> denied(Exception ignored) {
    return ResponseEntity.status(403).body(ApiResponse.error("Access denied", 403));
  }
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiResponse<Map<String,Object>>> illegal(IllegalArgumentException ex) {
    int status=ex.getMessage()!=null && (ex.getMessage().contains("Registration unavailable")||ex.getMessage().contains("already exists"))?409:400;
    return ResponseEntity.status(status).body(new ApiResponse<>(false, status==409?"Identity already exists":"Request could not be processed", Map.of(), status));
  }
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiResponse<Map<String,Object>>> malformed(HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Malformed request", Map.of(), 400));
  }
  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Map<String,Object>>> error(Exception ignored) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(false, "Internal server error", Map.of(), 500));
  }
}
