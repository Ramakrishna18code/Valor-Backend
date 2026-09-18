package com.valor.communication;

import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static com.valor.communication.CommunicationDtos.*;

@RestController
@RequestMapping("/api/v1/admin/communications")
class CommunicationController {
    private final CommunicationService service;
    CommunicationController(CommunicationService service) { this.service = service; }

    @GetMapping("/messages")
    ApiResponse<PageView> messages(@RequestParam(required=false) CommunicationStatus status,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ApiResponse.success("Communication messages", service.list(status, page, size), 200);
    }

    @PostMapping("/events")
    ApiResponse<java.util.List<MessageView>> create(@Valid @RequestBody SendRequest input) {
        return ApiResponse.success("Communication event", service.enqueue(input), 200);
    }

    @PostMapping("/messages/{id}/process")
    ApiResponse<MessageView> process(@PathVariable Long id) {
        return ApiResponse.success("Communication message", service.process(id), 200);
    }

    @PostMapping("/retries/process")
    ApiResponse<Integer> retries(@RequestParam(defaultValue="25") int limit) {
        return ApiResponse.success("Communication retries", service.processDueRetries(limit), 200);
    }

    @GetMapping("/preferences/{userId}")
    ApiResponse<PreferenceView> preferences(@PathVariable Long userId) {
        return ApiResponse.success("Communication preferences", service.preferences(userId), 200);
    }

    @PutMapping("/preferences/{userId}")
    ApiResponse<PreferenceView> preferences(@PathVariable Long userId, @Valid @RequestBody PreferenceRequest input) {
        return ApiResponse.success("Communication preferences", service.preferences(userId, input), 200);
    }
}
