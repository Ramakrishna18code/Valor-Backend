package com.valor.controller;

import com.valor.entity.Notification;
import com.valor.request.NotificationRequest;
import com.valor.response.ApiResponse;
import com.valor.response.NotificationResponse;
import com.valor.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Notifications", description = "Email, SMS, and push notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "Create notification")
    @PostMapping
    public ResponseEntity<ApiResponse<NotificationResponse>> createNotification(@Valid @RequestBody NotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Notification created", toResponse(notificationService.createNotification(request)), HttpStatus.CREATED.value()));
    }

    @Operation(summary = "Get notification")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationResponse>> getNotification(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Notification fetched", toResponse(notificationService.getNotification(id)), HttpStatus.OK.value()));
    }

    @Operation(summary = "List notifications")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getAllNotifications() {
        return ResponseEntity.ok(ApiResponse.success("Notifications fetched", notificationService.getAllNotifications().stream().map(this::toResponse).collect(Collectors.toList()), HttpStatus.OK.value()));
    }

    @Operation(summary = "Search notifications")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> searchNotifications(
            @RequestParam String term,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "scheduledAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<NotificationResponse> response = notificationService.searchNotifications(term, pageable).map(this::toResponse);
        return ResponseEntity.ok(ApiResponse.success("Notifications search completed", response, HttpStatus.OK.value()));
    }

    @Operation(summary = "Filter notifications by recipient type")
    @GetMapping("/recipient/{recipientType}")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getByRecipientType(@PathVariable String recipientType,
                                                                                      @RequestParam(defaultValue = "0") int page,
                                                                                      @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("scheduledAt").descending());
        Page<NotificationResponse> response = notificationService.getByRecipientType(recipientType, pageable).map(this::toResponse);
        return ResponseEntity.ok(ApiResponse.success("Notifications fetched", response, HttpStatus.OK.value()));
    }

    @Operation(summary = "Mark notification as sent")
    @PutMapping("/{id}/sent")
    public ResponseEntity<ApiResponse<NotificationResponse>> markSent(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Notification marked as sent", toResponse(notificationService.markSent(id)), HttpStatus.OK.value()));
    }

    @Operation(summary = "Mark notification as read")
    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", toResponse(notificationService.markRead(id)), HttpStatus.OK.value()));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getRecipientType(),
                notification.getRecipientId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getChannel(),
                notification.getStatus(),
                notification.getScheduledAt(),
                notification.getSentAt(),
                notification.getReadAt()
        );
    }
}