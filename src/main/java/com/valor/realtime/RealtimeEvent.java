package com.valor.realtime;

import java.time.Instant;
import java.util.Set;

public record RealtimeEvent(String type, String entityType, Long entityId, String status,
        Instant occurredAt, Set<Long> recipientUserIds) {
    public RealtimeEvent {
        recipientUserIds = recipientUserIds == null ? Set.of() : Set.copyOf(recipientUserIds);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}
