package com.valor.notifications;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public final class NotificationDtos {
    private NotificationDtos() {}
    @Schema(name="NotificationCreateRequest")
    public record Create(@NotNull @Positive Long recipientUserId,
            @NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 2000) String message,
            NotificationChannel channel, LocalDateTime scheduledAt) {
        @JsonAnySetter public void rejectUnknown(String field, JsonNode value) {
            throw new IllegalArgumentException("Unsupported notification field");
        }
    }
    @Schema(name="NotificationResponse")
    public record View(Long id, Long recipientUserId, String title, String message,
            NotificationChannel channel, NotificationStatus status, LocalDateTime scheduledAt,
            LocalDateTime sentAt, LocalDateTime readAt, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    @Schema(name="NotificationPageResponse")
    public record PageView(List<View> items, int page, int size, long totalElements, int totalPages) {}
}
