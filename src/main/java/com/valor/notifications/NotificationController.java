package com.valor.notifications;

import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import static com.valor.notifications.NotificationDtos.*;

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {
    private final NotificationService service;
    NotificationController(NotificationService service) { this.service = service; }
    @Operation(operationId="getNotifications")
    @GetMapping
    ApiResponse<PageView> inbox(@RequestParam(required = false) NotificationStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Success", service.inbox(status, page, size), 200);
    }
    @Operation(operationId="createNotification")
    @PostMapping
    ApiResponse<View> create(@Valid @RequestBody Create input) {
        return ApiResponse.success("Notification created", service.create(input), 200);
    }
    @Operation(operationId="markNotificationRead")
    @PutMapping("/{id}/read")
    ApiResponse<View> read(@PathVariable Long id) {
        return ApiResponse.success("Notification read", service.read(id), 200);
    }
}
