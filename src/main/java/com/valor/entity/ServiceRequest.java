package com.valor.entity;

import com.valor.audit.AuditableEntity;
import com.valor.enums.JobStatus;
import com.valor.enums.PriorityLevel;
import com.valor.enums.ServiceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_requests")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceRequest extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String serviceId;

    private String title;
    private String description;
    private String issueCategory;

    @Enumerated(EnumType.STRING)
    private PriorityLevel priority;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @Enumerated(EnumType.STRING)
    private ServiceType serviceType;

    private String customerRemarks;
    private String technicianRemarks;

    private LocalDateTime serviceRequestedAt;
    private LocalDate preferredVisitDate;
    private String preferredTimeSlot;
    private String internalAdminNotes;
    private LocalDateTime assignedAt;
    private LocalDateTime startedAt;
    private LocalDateTime pausedAt;
    private LocalDateTime resumedAt;
    private LocalDateTime completedAt;
    private String customerSignaturePath;
    private String serviceReportPath;
    private Integer estimatedCompletionMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_technician_id")
    private Technician assignedTechnician;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lift_id")
    private Lift lift;
}
