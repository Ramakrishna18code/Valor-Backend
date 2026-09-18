package com.valor.communication;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;

final class CommunicationDtos {
    private CommunicationDtos() {}
    record SendRequest(@NotBlank @Size(max=80) String eventType, @NotNull @Positive Long recipientUserId,
            @NotEmpty Set<CommunicationChannel> channels, @Size(max=120) String templateKey,
            Map<String,String> variables, @NotBlank @Size(max=160) String idempotencyKey) {
        @JsonAnySetter public void rejectUnknown(String k, JsonNode v) { throw new IllegalArgumentException("Unsupported communication field"); }
    }
    record PreferenceRequest(boolean emailEnabled, boolean smsEnabled, boolean whatsappEnabled, boolean inAppEnabled) {}
    record PreferenceView(Long userId, boolean emailEnabled, boolean smsEnabled, boolean whatsappEnabled, boolean inAppEnabled) {}
    record MessageView(Long id, Long eventId, String eventType, Long recipientUserId, String recipientMasked,
            CommunicationChannel channel, CommunicationStatus status, String provider, String providerMessageId,
            String templateKey, int retryCount, int maxRetryCount, LocalDateTime nextRetryAt,
            String failureReason, LocalDateTime sentAt, LocalDateTime deliveredAt, LocalDateTime failedAt,
            LocalDateTime createdAt, LocalDateTime updatedAt) {}
    record PageView(List<MessageView> items, int page, int size, long totalElements, int totalPages) {}
}
