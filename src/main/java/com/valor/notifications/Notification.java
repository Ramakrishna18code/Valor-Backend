package com.valor.notifications;

import com.valor.assets.AssetRecord;
import com.valor.auth.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "Notification")
@Table(name = "notifications")
@Getter @Setter
public class Notification extends AssetRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipient;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 2000)
    private String message;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel = NotificationChannel.IN_APP;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    @Column(name = "read_at")
    private LocalDateTime readAt;
}
