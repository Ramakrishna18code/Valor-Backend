package com.valor.workflow;

import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;
import com.valor.auth.*;
import com.valor.assets.AssetRecord;
import com.valor.assets.Lift;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "ServiceRequest")
@Table(name = "service_requests")
@Getter @Setter
public class ServiceRequest extends AssetRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfile customer;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lift_id")
    private Lift lift;
    @Column(name = "service_id", nullable = false, length = 80)
    private String serviceId;
    @Column(name = "title", nullable = false, length = 200)
    private String title;
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;
    @Column(name = "issue_category", nullable = true, length = 100)
    private String issueCategory;
    @Column(name = "priority", nullable = false, length = 20)
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private RequestPriority priority = RequestPriority.MEDIUM;
    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private RequestStatus status = RequestStatus.PENDING;
    @Column(name = "service_type", nullable = false, length = 40)
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private WorkflowServiceType serviceType;
    @Column(name = "customer_remarks", nullable = true, length = 2000)
    private String customerRemarks;
    @Column(name = "technician_remarks", nullable = true, length = 2000)
    private String technicianRemarks;
    @Column(name = "service_requested_at", nullable = false)
    private LocalDateTime serviceRequestedAt = LocalDateTime.now();
    @Column(name = "preferred_visit_date", nullable = true)
    private LocalDate preferredVisitDate;
    @Column(name = "preferred_time_slot", nullable = true, length = 80)
    private String preferredTimeSlot;
    @Column(name = "internal_admin_notes", nullable = true, length = 2000)
    private String internalAdminNotes;
    @Column(name = "completed_at", nullable = true)
    private LocalDateTime completedAt;
    @Column(name = "estimated_completion_minutes", nullable = true)
    private Integer estimatedCompletionMinutes;
}
