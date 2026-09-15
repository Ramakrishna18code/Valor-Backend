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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "VisitChangeRequest")
@Table(name = "visit_change_requests")
@Getter
@Setter
public class VisitChangeRequest extends AssetRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest request;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_visit_id")
    private ServiceVisit visit;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_technician_profile_id", nullable = false)
    private TechnicianProfile requestedTechnician;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "request_type", nullable = false, length = 30)
    private VisitChangeRequestType type;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private VisitChangeRequestStatus status = VisitChangeRequestStatus.PENDING;
    @Column(name = "reason", nullable = false, length = 2000)
    private String reason;
    @Column(name = "requested_date", nullable = false)
    private LocalDate requestedDate;
    @Column(name = "requested_start_time", nullable = false)
    private LocalTime requestedStartTime;
    @Column(name = "requested_end_time", nullable = false)
    private LocalTime requestedEndTime;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    @Column(name = "review_notes", length = 2000)
    private String reviewNotes;
}
