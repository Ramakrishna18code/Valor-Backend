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

@Entity(name = "ServiceReport")
@Table(name = "service_reports")
@Getter @Setter
public class ServiceReport extends AssetRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private TechnicianAssignment assignment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_by_user_id", nullable = false)
    private User reportedBy;
    @Column(name = "diagnosis", nullable = false, columnDefinition = "TEXT")
    private String diagnosis;
    @Column(name = "work_performed", nullable = false, columnDefinition = "TEXT")
    private String workPerformed;
    @Column(name = "testing_result", nullable = false, columnDefinition = "TEXT")
    private String testingResult;
    @Column(name = "completion_notes", nullable = true, columnDefinition = "TEXT")
    private String completionNotes;
}
