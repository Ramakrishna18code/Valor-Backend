package com.valor.workflow;

import com.valor.workflow.ChecklistDtos.JobChecklistView;
import com.valor.response.ApiResponse;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ConstraintViolationException;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.valor.workflow")
class WorkflowErrorHandler {
    private final ChecklistInitializationRecovery checklistRecovery;

    WorkflowErrorHandler(ChecklistInitializationRecovery checklistRecovery) {
        this.checklistRecovery = checklistRecovery;
    }

    @ExceptionHandler(ChecklistInitializationRace.class)
    ResponseEntity<ApiResponse<JobChecklistView>> checklistInitializationRace(ChecklistInitializationRace error) {
        JobChecklistView recovered = checklistRecovery.recover(error.requestId);
        if (recovered == null) return ResponseEntity.status(409).body(ApiResponse.error("Workflow conflict", 409));
        return ResponseEntity.ok(ApiResponse.success("Success", recovered, 200));
    }

    @ExceptionHandler(WorkflowException.class)
    ResponseEntity<ApiResponse<Object>> workflow(WorkflowException error) { return response(error.status, error.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Object>> invalidFields(MethodArgumentNotValidException error) {
        String fields = error.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField).distinct().sorted().reduce((left, right) -> left + ", " + right).orElse("");
        return response(400, fields.isEmpty() ? "Invalid workflow request" : "Invalid workflow fields: " + fields);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Object>> invalidBody(HttpMessageNotReadableException error) {
        Throwable cause = error.getMostSpecificCause();
        if (cause instanceof UnrecognizedPropertyException unknown) {
            return response(400, "Unsupported workflow field: " + unknown.getPropertyName());
        }
        if (cause instanceof InvalidFormatException invalidFormat) {
            String field = invalidFormat.getPath().stream().map(JsonMappingException.Reference::getFieldName)
                    .filter(Objects::nonNull).reduce((left, right) -> right).orElse(null);
            if (field != null) return response(400, "Invalid value for workflow field: " + field);
        }
        return response(400, "Invalid workflow request. Check field names and value formats.");
    }
    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<Object>> invalid(Exception ignored) {
        return response(400, "Invalid workflow request. Check required fields and value formats.");
    }
    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ApiResponse<Object>> conflict(Exception ignored) { return response(409, "Workflow conflict"); }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiResponse<Object>> unauthenticated(Exception ignored) { return response(401, "Authentication required"); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Object>> forbidden(Exception ignored) { return response(403, "You do not have permission to access this information."); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Object>> unexpected(Exception ignored) { return response(500, "Internal server error"); }
    private ResponseEntity<ApiResponse<Object>> response(int status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(message, status));
    }
}
