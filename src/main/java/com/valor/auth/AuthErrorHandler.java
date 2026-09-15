package com.valor.auth;

import com.valor.response.ApiResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.valor.auth")
class AuthErrorHandler {
  @ExceptionHandler(StaffNotFoundException.class)
  ResponseEntity<ApiResponse<Object>> missingStaff(Exception ignored) {
    return ResponseEntity.status(404).body(ApiResponse.error("Staff not found", 404));
  }
  @ExceptionHandler(CustomerNotFoundException.class)
  ResponseEntity<ApiResponse<Object>> missingCustomer(Exception ignored) {
    return ResponseEntity.status(404).body(ApiResponse.error("Customer not found", 404));
  }
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<ApiResponse<Object>> duplicate(Exception ignored) {
    return ResponseEntity.status(409).body(ApiResponse.error("Identity already exists", 409));
  }
  @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
      org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
  ResponseEntity<ApiResponse<Object>> invalid(Exception ignored) {
    return ResponseEntity.status(400).body(ApiResponse.error("Invalid request", 400));
  }
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
