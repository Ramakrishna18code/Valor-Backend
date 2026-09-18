package com.valor.auth;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuditService {
    private final AuditLogRepo logs;
    private final AssetIdentityAccess identities;

    AuditService(AuditLogRepo logs, AssetIdentityAccess identities) {
        this.logs = logs;
        this.identities = identities;
    }

    public void record(String action, String entityType, Object entityId, String summary) {
        record(action, entityType, entityId, summary, null, null, "SUCCESS");
    }

    public void record(String action, String entityType, Object entityId, String summary, String before, String after, String status) {
        AuditLog log = new AuditLog();
        try {
            User actor = identities.actor();
            log.actorUserId = actor.getId();
            log.actorRole = actor.getRole().name();
        } catch (RuntimeException ignored) {
            log.actorRole = "SYSTEM";
        }
        log.action = clean(action, 100);
        log.entityType = clean(entityType, 80);
        log.entityId = entityId == null ? null : clean(String.valueOf(entityId), 80);
        log.summary = clean(summary, 1000);
        log.beforeSummary = clean(before, 1000);
        log.afterSummary = clean(after, 1000);
        log.resultStatus = clean(status == null ? "SUCCESS" : status, 40);
        log.createdAt = LocalDateTime.now();
        logs.save(log);
    }

    private static String clean(String value, int max) {
        if (value == null) return null;
        String safe = value.replaceAll("(?i)(password|token|secret|otp|cvv|pin)\\s*[:=]\\s*\\S+", "$1=<redacted>").trim();
        return safe.length() > max ? safe.substring(0, max) : safe;
    }
}
