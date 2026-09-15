package com.valor.workflow;

import com.valor.assets.AssetRecord;
import com.valor.auth.TechnicianProfile;
import com.valor.auth.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "VisitHistory")
@Table(name = "service_visit_history")
@Getter
@Setter
public class VisitHistory extends AssetRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_visit_id", nullable = false)
    private ServiceVisit visit;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_user_id", nullable = false)
    private User changedBy;
    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 20)
    private VisitStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", length = 20)
    private VisitStatus toStatus;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_technician_profile_id")
    private TechnicianProfile fromTechnician;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_technician_profile_id")
    private TechnicianProfile toTechnician;
    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;
    @Column(name = "start_time")
    private LocalTime startTime;
    @Column(name = "end_time")
    private LocalTime endTime;
    @Column(name = "reason", length = 2000)
    private String reason;
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    public VisitHistory() {}
    public VisitHistory(ServiceVisit visit, User actor, String eventType, String reason) {
        this.visit = visit; this.request = visit.getRequest(); this.changedBy = actor;
        this.eventType = eventType; this.reason = reason;
    }
}
