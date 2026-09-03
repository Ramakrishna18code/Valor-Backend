package com.valor.request;

import com.valor.enums.NotificationChannel;
import com.valor.enums.NotificationStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record NotificationRequest(
        @NotBlank(message = "Recipient type is required") String recipientType,
        Long recipientId,
        @NotBlank(message = "Title is required") String title,
        @NotBlank(message = "Message is required") String message,
        NotificationChannel channel,
        NotificationStatus status,
        LocalDateTime scheduledAt
) {
}