package com.valor.auth;
import io.swagger.v3.oas.annotations.Operation;

import com.valor.response.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {
    record Health(String status) {}
    @Operation(operationId="health")
    @GetMapping("/api/v1/health")
    ApiResponse<Health> health() {
        return ApiResponse.success("Healthy", new Health("UP"), 200);
    }
}
