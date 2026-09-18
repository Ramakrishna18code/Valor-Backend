package com.valor.communication;

import com.valor.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackages = "com.valor.communication")
class CommunicationErrorHandler {
    @ExceptionHandler(CommunicationException.class)
    ResponseEntity<ApiResponse<Void>> communication(CommunicationException ex) {
        return ResponseEntity.status(ex.status).body(ApiResponse.error(ex.getMessage(), ex.status));
    }
}
