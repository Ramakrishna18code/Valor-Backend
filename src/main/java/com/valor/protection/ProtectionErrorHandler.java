package com.valor.protection;

import com.valor.response.ApiResponse;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.FieldError;

@RestControllerAdvice(basePackages = "com.valor.protection")
class ProtectionErrorHandler {
    @ExceptionHandler(ProtectionException.class)
    ResponseEntity<ApiResponse<Object>> protection(ProtectionException error) { return response(error.status, error.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Object>> validation(MethodArgumentNotValidException error) {
        String fields = error.getBindingResult().getFieldErrors().stream().map(FieldError::getField).distinct().sorted()
                .reduce((left, right) -> left + ", " + right).orElse("");
        return response(400, fields.isEmpty() ? "Invalid protection request" : "Invalid protection fields: " + fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Object>> unreadable(HttpMessageNotReadableException error) {
        Throwable cause = error.getMostSpecificCause();
        if (cause instanceof UnrecognizedPropertyException unknown)
            return response(400, "Unsupported protection field: " + unknown.getPropertyName());
        if (cause instanceof InvalidFormatException invalid) {
            String field = invalid.getPath().stream().map(JsonMappingException.Reference::getFieldName)
                    .filter(java.util.Objects::nonNull).reduce((left, right) -> right).orElse(null);
            if (field != null) return response(400, "Invalid value for protection field: " + field);
        }
        return response(400, "Invalid protection request. Check field names and value formats.");
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<Object>> invalid(Exception ignored) { return response(400, "Invalid protection request"); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Object>> conflict(Exception ignored) { return response(409, "Protection record conflicts with existing data"); }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Object>> denied(Exception ignored) { return response(403, "Access denied"); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Object>> unexpected(Exception ignored) { return response(500, "Internal server error"); }

    private ResponseEntity<ApiResponse<Object>> response(int status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(message, status));
    }
}

class ProtectionException extends RuntimeException {
    final int status;
    ProtectionException(int status, String message) { super(message); this.status = status; }
}
