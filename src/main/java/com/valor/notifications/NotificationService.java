package com.valor.notifications;

import com.valor.auth.AssetIdentityAccess;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.valor.notifications.NotificationDtos.*;

@Service
@Transactional
public class NotificationService {
    private final NotificationRepository notifications;
    private final NotificationRecipientRepository recipients;
    private final AssetIdentityAccess identities;
    private final Clock clock;
    public NotificationService(NotificationRepository notifications, NotificationRecipientRepository recipients,
            AssetIdentityAccess identities, Clock clock) {
        this.notifications = notifications; this.recipients = recipients; this.identities = identities; this.clock = clock;
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
        return view(notification);
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
