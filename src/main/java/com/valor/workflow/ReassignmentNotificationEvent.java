package com.valor.workflow;

public record ReassignmentNotificationEvent(Long requestId, Long serviceRequestId,
        Long incomingUserId, Long outgoingUserId) {}
