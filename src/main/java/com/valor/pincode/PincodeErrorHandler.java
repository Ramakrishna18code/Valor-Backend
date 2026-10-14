package com.valor.pincode;
import com.valor.response.ApiResponse; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(basePackageClasses=PincodeController.class) class PincodeErrorHandler { @ExceptionHandler(PincodeException.class) ResponseEntity<ApiResponse<Object>> handle(PincodeException e){return ResponseEntity.status(e.status).body(ApiResponse.error(e.getMessage(),e.status));} }
