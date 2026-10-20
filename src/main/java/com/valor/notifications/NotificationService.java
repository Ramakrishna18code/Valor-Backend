package com.valor.notifications;

import com.valor.auth.AssetIdentityAccess;
import com.valor.auth.AuditService;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import static com.valor.notifications.NotificationDtos.*;

@Service
@Transactional
public class NotificationService {
    private final NotificationRepository notifications;
    private final NotificationRecipientRepository recipients;
    private final AssetIdentityAccess identities;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate idempotentWrite;
    public NotificationService(NotificationRepository notifications, NotificationRecipientRepository recipients,
            AssetIdentityAccess identities, AuditService audit, Clock clock, PlatformTransactionManager transactionManager) {
        this.notifications = notifications; this.recipients = recipients; this.identities = identities; this.audit = audit; this.clock = clock;
        this.idempotentWrite = new TransactionTemplate(transactionManager);
        this.idempotentWrite.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public View create(Create input) {
        identities.requireAdmin();
        if (input.channel() != null && input.channel() != NotificationChannel.IN_APP) {
            throw new NotificationException(400, "Only IN_APP notifications are available");
        }
        var recipient = recipients.findById(input.recipientUserId())
                .orElseThrow(() -> new NotificationException(404, "Recipient not found"));
        if (!recipient.isActive()) throw new NotificationException(400, "Recipient is inactive");
        Notification notification = new Notification();
        notification.setRecipient(recipient); notification.setTitle(input.title()); notification.setMessage(input.message());
        notification.setScheduledAt(input.scheduledAt());
        notifications.save(notification); notifications.flush();
        audit.record("NOTIFICATION_CREATE", "NOTIFICATION", notification.getId(),
                "Created notification recipient=" + recipient.getId(),
                null, "recipient=" + recipient.getId() + ",channel=" + notification.getChannel() + ",status=" + notification.getStatus()
                        + ",scheduledAt=" + notification.getScheduledAt(), "SUCCESS");
        return view(notification);
    }

    public View createSystem(Long recipientUserId, String title, String message) {
        return createSystemOnce(recipientUserId, null, title, message);
    }

    public View createSystemOnce(Long recipientUserId, String eventKey, String title, String message) {
        String normalizedEventKey = eventKey == null || eventKey.isBlank() ? null : eventKey.trim();
        if (normalizedEventKey != null) {
            var existing = notifications.findByEventKey(normalizedEventKey);
            if (existing.isPresent()) return view(existing.get());
        }
        try {
            return idempotentWrite.execute(status -> {
                var recipient = recipients.findById(recipientUserId)
                        .orElseThrow(() -> new NotificationException(404, "Recipient not found"));
                Notification notification = new Notification();
                notification.setRecipient(recipient);
                notification.setTitle(title == null || title.isBlank() ? "Valor update" : title);
                notification.setMessage(message == null || message.isBlank() ? "You have a new Valor update." : message);
                notification.setChannel(NotificationChannel.IN_APP);
                notification.setStatus(NotificationStatus.PENDING);
                notification.setEventKey(normalizedEventKey);
                notifications.save(notification); notifications.flush();
                return view(notification);
            });
        } catch (DataIntegrityViolationException ex) {
            if (normalizedEventKey == null || !isEventKeyConflict(ex)) throw ex;
            return notifications.findByEventKey(normalizedEventKey).map(this::view)
                    .orElseThrow(() -> ex);
        }
    }

    private static boolean isEventKeyConflict(DataIntegrityViolationException ex) {
        String text = ex.toString().toLowerCase(java.util.Locale.ROOT);
        return text.contains("event_key") || text.contains("ux_notifications_event_key");
    }

    @Transactional(readOnly = true)
    public PageView inbox(NotificationStatus status, int page, int size) {
        Long recipient = identities.actor().getId();
        if (page < 0 || size < 1 || size > 100) throw new NotificationException(400, "Invalid page");
        LocalDateTime asOf = now();
        var rows = notifications.inbox(recipient, asOf, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new PageView(rows.getContent().stream().map(this::view).toList(), rows.getNumber(), rows.getSize(),
                rows.getTotalElements(), rows.getTotalPages());
    }

    public View read(Long id) {
        Long recipient = identities.actor().getId();
        Notification notification = notifications.forRead(id)
                .orElseThrow(() -> new NotificationException(404, "Notification not found"));
        if (!notification.getRecipient().getId().equals(recipient)) throw new AccessDeniedException("Access denied");
        LocalDateTime asOf = now();
        if (notification.getChannel() != NotificationChannel.IN_APP
                || (notification.getScheduledAt() != null && notification.getScheduledAt().isAfter(asOf))) {
            throw new NotificationException(404, "Notification not found");
        }
        if (notification.getStatus() != NotificationStatus.READ) {
            notification.setStatus(NotificationStatus.READ); notification.setReadAt(asOf);
            notifications.flush();
        }
        return view(notification);
    }

    private LocalDateTime now() { return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS); }
    private View view(Notification n) {
        return new View(n.getId(), n.getRecipient().getId(), n.getTitle(), n.getMessage(), n.getChannel(), n.getStatus(),
                n.getScheduledAt(), n.getSentAt(), n.getReadAt(), n.getCreatedAt(), n.getUpdatedAt());
    }
}
