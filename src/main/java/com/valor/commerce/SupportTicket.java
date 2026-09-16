package com.valor.commerce;

import com.valor.auth.User;
import com.valor.workflow.ServiceRequest;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "SupportTicket")
@Table(name = "support_tickets")
@Getter @Setter
class SupportTicket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "ticket_reference", unique = true, length = 40)
    private String ticketReference;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "service_request_id")
    private ServiceRequest serviceRequest;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "category", nullable = false, length = 40)
    private SupportTicketCategory category = SupportTicketCategory.OTHER;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "priority", nullable = false, length = 20)
    private SupportTicketPriority priority = SupportTicketPriority.MEDIUM;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private SupportTicketStatus status = SupportTicketStatus.SUBMITTED;
    @Column(name = "subject", nullable = false, length = 200)
    private String subject;
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;
    @Column(name = "preferred_contact", length = 80)
    private String preferredContact;
    @Column(name = "admin_notes", length = 2000)
    private String adminNotes;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist void pre() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void upd() { updatedAt = LocalDateTime.now(); }
}
