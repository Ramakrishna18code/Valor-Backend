package com.valor.communication;

import com.valor.auth.User;
import com.valor.auth.Role;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

enum CommunicationChannel { EMAIL, SMS, WHATSAPP, IN_APP }
enum CommunicationStatus { PENDING, PROCESSING, SENT, DELIVERED, FAILED, CANCELLED }

@Entity(name = "CommunicationTemplate")
@Table(name = "communication_templates")
class CommunicationTemplate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 80) String eventType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) CommunicationChannel channel;
    @Column(nullable = false, length = 120) String templateKey;
    @Column(length = 200) String subject;
    @Column(nullable = false, columnDefinition = "TEXT") String body;
    @Column(length = 1000) String variables;
    @Column(nullable = false) boolean active = true;
    @Column(nullable = false) LocalDateTime createdAt;
    @Column(nullable = false) LocalDateTime updatedAt;
    @PrePersist void pre() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void upd() { updatedAt = LocalDateTime.now(); }
}

@Entity(name = "CommunicationPreference")
@Table(name = "communication_preferences")
class CommunicationPreference {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) User user;
    @Column(nullable = false) boolean emailEnabled = true;
    @Column(nullable = false) boolean smsEnabled = true;
    @Column(nullable = false) boolean whatsappEnabled = true;
    @Column(nullable = false) boolean inAppEnabled = true;
    @Column(nullable = false) boolean otpSmsEnabled = true;
    @Column(nullable = false) boolean otpWhatsappEnabled;
    @Column(nullable = false) boolean serviceNotificationsEnabled = true;
    @Column(nullable = false) boolean billingNotificationsEnabled = true;
    @Column(nullable = false) boolean appointmentNotificationsEnabled = true;
    @Column(nullable = false) boolean jobNotificationsEnabled = true;
    @Column(nullable = false) boolean visitNotificationsEnabled = true;
    @Column(nullable = false) boolean systemNotificationsEnabled = true;
    @Column(nullable = false) boolean criticalAlertsEnabled = true;
    @Column(nullable = false) boolean reportNotificationsEnabled = true;
    @Column(nullable = false) LocalDateTime createdAt;
    @Column(nullable = false) LocalDateTime updatedAt;
    @PrePersist void pre() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void upd() { updatedAt = LocalDateTime.now(); }
}

@Entity(name = "CommunicationEvent")
@Table(name = "communication_events")
class CommunicationEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 80) String eventType;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recipient_user_id", nullable = false) User recipient;
    @Column(nullable = false, length = 160) String idempotencyKey;
    @Column(columnDefinition = "TEXT") String payload;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) CommunicationStatus status = CommunicationStatus.PENDING;
    @Column(nullable = false) LocalDateTime createdAt;
    @Column(nullable = false) LocalDateTime updatedAt;
    @PrePersist void pre() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void upd() { updatedAt = LocalDateTime.now(); }
}

@Entity(name = "CommunicationMessage")
@Table(name = "communication_messages")
class CommunicationMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "event_id", nullable = false) CommunicationEvent event;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recipient_user_id", nullable = false) User recipient;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) CommunicationChannel channel;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "template_id") CommunicationTemplate template;
    @Column(length = 120) String templateKey;
    @Column(length = 80) String provider;
    @Column(length = 160) String providerMessageId;
    @Column(length = 160) String recipientMasked;
    @Column(length = 200) String subject;
    @Column(columnDefinition = "TEXT") String htmlBody;
    @Column(columnDefinition = "TEXT") String body;
    @Column(length = 254) String fromAddress;
    @Column(length = 120) String fromName;
    @Column(length = 254) String replyTo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) CommunicationStatus status = CommunicationStatus.PENDING;
    @Column(nullable = false) int retryCount;
    @Column(nullable = false) int maxRetryCount = 3;
    LocalDateTime nextRetryAt;
    @Column(length = 1000) String failureReason;
    LocalDateTime sentAt;
    LocalDateTime deliveredAt;
    LocalDateTime failedAt;
    @Column(nullable = false) LocalDateTime createdAt;
    @Column(nullable = false) LocalDateTime updatedAt;
    @PrePersist void pre() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void upd() { updatedAt = LocalDateTime.now(); }
}

interface CommunicationTemplateRepository extends JpaRepository<CommunicationTemplate, Long> {
    Optional<CommunicationTemplate> findTopByEventTypeAndChannelAndActiveTrueOrderByUpdatedAtDesc(String eventType, CommunicationChannel channel);
}
interface CommunicationPreferenceRepository extends JpaRepository<CommunicationPreference, Long> {
    Optional<CommunicationPreference> findByUserId(Long userId);
}
interface CommunicationEventRepository extends JpaRepository<CommunicationEvent, Long> {
    Optional<CommunicationEvent> findByIdempotencyKey(String idempotencyKey);
}
interface CommunicationMessageRepository extends JpaRepository<CommunicationMessage, Long> {
    Optional<CommunicationMessage> findByEventIdAndChannel(Long eventId, CommunicationChannel channel);
    @org.springframework.data.jpa.repository.Query(value = "select m from CommunicationMessage m join fetch m.event e join fetch m.recipient r where (:status is null or m.status=:status)",
            countQuery = "select count(m) from CommunicationMessage m where (:status is null or m.status=:status)")
    Page<CommunicationMessage> list(@Param("status") CommunicationStatus status, Pageable pageable);
    @org.springframework.data.jpa.repository.Query("select m from CommunicationMessage m join fetch m.event e join fetch m.recipient r where m.id=:id")
    Optional<CommunicationMessage> detail(@Param("id") Long id);
    @org.springframework.data.jpa.repository.Query(value = "select m from CommunicationMessage m join fetch m.event e join fetch m.recipient r where m.status=com.valor.communication.CommunicationStatus.FAILED and m.retryCount < m.maxRetryCount and (m.nextRetryAt is null or m.nextRetryAt <= :now)",
            countQuery = "select count(m) from CommunicationMessage m where m.status=com.valor.communication.CommunicationStatus.FAILED and m.retryCount < m.maxRetryCount and (m.nextRetryAt is null or m.nextRetryAt <= :now)")
    Page<CommunicationMessage> dueRetries(@Param("now") LocalDateTime now, Pageable pageable);
}
interface CommunicationUserRepository extends org.springframework.data.repository.Repository<User, Long> {
    Optional<User> findById(Long id);
    @org.springframework.data.jpa.repository.Query("select u from User u where u.role in :roles and u.active=true")
    java.util.List<User> activeRoles(@Param("roles") java.util.Collection<Role> roles);
}
