package com.valor.workflow;

import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import com.valor.auth.*;
import com.valor.assets.AssetRecord;
import com.valor.assets.Lift;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "ServiceStatusHistory")
@Table(name = "service_status_history")
@Getter
@org.hibernate.annotations.Immutable
public class ServiceStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_user_id", nullable = false)
    private User changedBy;
    @Column(name = "from_status", nullable = true, length = 30)
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private RequestStatus fromStatus;
    @Column(name = "to_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private RequestStatus toStatus;
    @Column(name = "notes", nullable = true, length = 2000)
    private String notes;
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    protected ServiceStatusHistory() {}
    public ServiceStatusHistory(ServiceRequest request, RequestStatus from, RequestStatus to, User actor, String notes) {
        this.request = request; this.fromStatus = from; this.toStatus = to; this.changedBy = actor;
        this.notes = notes; this.changedAt = LocalDateTime.now();
    }
}
