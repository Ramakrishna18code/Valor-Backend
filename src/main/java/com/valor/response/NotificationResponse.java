package com.valor.response;

import com.valor.enums.NotificationChannel;
import com.valor.enums.NotificationStatus;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String recipientType,
        Long recipientId,
        String title,
        String message,
        NotificationChannel channel,
        NotificationStatus status,
        LocalDateTime scheduledAt,
        LocalDateTime sentAt,
        LocalDateTime readAt
) {
}