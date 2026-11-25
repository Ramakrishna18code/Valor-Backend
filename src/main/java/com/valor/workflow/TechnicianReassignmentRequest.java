package com.valor.workflow;

import com.valor.assets.AssetRecord;
import com.valor.auth.TechnicianProfile;
import com.valor.auth.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "technician_reassignment_requests")
@Getter @Setter
public class TechnicianReassignmentRequest extends AssetRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest serviceRequest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "requesting_technician_id", nullable = false)
    private TechnicianProfile requestingTechnician;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "original_assignment_id", nullable = false)
    private TechnicianAssignment originalAssignment;
    @Enumerated(EnumType.STRING) @Column(name = "reason_code", nullable = false, length = 40)
    private ReassignmentReason reasonCode;
    @Column(name = "reason_explanation", nullable = false, length = 2000) private String reasonExplanation;
    @Column(name = "work_completed", length = 2000) private String workCompleted;
    @Column(name = "work_pending", length = 2000) private String workPending;
    @Column(name = "parts_safety_context", length = 2000) private String partsSafetyContext;
    @Column(name = "other_handover_context", length = 2000) private String otherHandoverContext;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReassignmentStatus status = ReassignmentStatus.PENDING;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by_user_id") private User reviewedBy;
    @Column(name = "decision_reason", length = 2000) private String decisionReason;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "replacement_technician_id") private TechnicianProfile replacementTechnician;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "new_assignment_id") private TechnicianAssignment newAssignment;
    @Column(name = "decided_at") private LocalDateTime decidedAt;
    @Column(name = "transferred_at") private LocalDateTime transferredAt;
    @Column(name = "withdrawn_at") private LocalDateTime withdrawnAt;
    @Column(name = "cancelled_at") private LocalDateTime cancelledAt;
    @Version @Column(nullable = false) private Long version;
}
