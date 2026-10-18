package com.valor.installation;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

enum InstallationMilestoneStatus { PLANNED, BILLABLE, INVOICED, PAID, CANCELLED }

@Entity(name = "InstallationMilestone")
@Table(name = "installation_milestones", uniqueConstraints = @UniqueConstraint(name = "uk_installation_milestone_code", columnNames = {"installation_request_id", "milestone_code"}))
@Getter @Setter
class InstallationMilestone {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "installation_request_id", nullable = false) private InstallationRequest request;
    @Column(name = "milestone_code", nullable = false, length = 80) private String milestoneCode;
    @Column(name = "sequence_no", nullable = false) private Integer sequenceNo;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 30) private InstallationMilestoneStatus status = InstallationMilestoneStatus.PLANNED;
    @Column(name = "amount", precision = 12, scale = 2) private BigDecimal amount;
    @Column(name = "currency", length = 3) private String currency;
    @Column(name = "invoice_id") private Long invoiceId;
    @Column(name = "payment_id") private Long paymentId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "schedule_id") private InstallationSchedule schedule;
    @Column(name = "billing_trigger", length = 80) private String billingTrigger;
    @Version @Column(name = "version", nullable = false) private Long version;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void pre() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
