package com.valor.auth;

import com.valor.response.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {
    @GetMapping("/api/v1/health")
    ApiResponse<Map<String, String>> health() {
        return ApiResponse.success("Healthy", Map.of("status", "UP"), 200);
    }
}
