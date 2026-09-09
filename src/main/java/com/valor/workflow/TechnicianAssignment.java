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

@Entity(name = "TechnicianAssignment")
@Table(name = "technician_assignments")
@Getter @Setter
public class TechnicianAssignment extends AssetRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id", nullable = false)
    private TechnicianProfile technician;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by_user_id", nullable = false)
    private User assignedBy;
    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private AssignmentStatus status = AssignmentStatus.ASSIGNED;
    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();
    @Column(name = "accepted_at", nullable = true)
    private LocalDateTime acceptedAt;
    @Column(name = "released_at", nullable = true)
    private LocalDateTime releasedAt;
    @Column(name = "notes", nullable = true, length = 2000)
    private String notes;
}
