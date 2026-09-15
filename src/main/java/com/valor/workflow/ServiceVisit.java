package com.valor.workflow;

import com.valor.assets.AssetRecord;
import com.valor.auth.TechnicianProfile;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "ServiceVisit")
@Table(name = "service_visits")
@Getter
@Setter
public class ServiceVisit extends AssetRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_profile_id", nullable = false)
    private TechnicianProfile technician;
    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private VisitStatus status = VisitStatus.SCHEDULED;
    @Column(name = "notes", length = 2000)
    private String notes;
}
