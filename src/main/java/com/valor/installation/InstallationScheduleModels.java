package com.valor.installation;

import com.valor.auth.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

enum InstallationScheduleStatus { DRAFT, AWAITING_APPROVAL, APPROVED, SUPERSEDED, CANCELLED }
enum InstallationScheduleApprovalType { COMPANY, CUSTOMER }

@Entity(name="InstallationSchedule") @Table(name="installation_schedules") @Getter @Setter
class InstallationSchedule {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="installation_request_id", nullable=false) InstallationRequest request;
    @Column(name="schedule_key", nullable=false, length=80) String scheduleKey;
    @Column(name="version_no", nullable=false) Integer versionNo;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) InstallationScheduleStatus status=InstallationScheduleStatus.DRAFT;
    @Column(length=3) String currency;
    @Column(name="milestone_definitions", columnDefinition="TEXT") String milestoneDefinitions;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="created_by_user_id", nullable=false) User createdBy;
    @Column(name="created_at", nullable=false) LocalDateTime createdAt;
    @Column(name="approved_at") LocalDateTime approvedAt;
    @Column(name="superseded_at") LocalDateTime supersededAt;
    @PrePersist void pre(){createdAt=LocalDateTime.now();}
}

@Entity(name="InstallationScheduleApproval") @Table(name="installation_schedule_approvals") @Getter @Setter
class InstallationScheduleApproval {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="schedule_id", nullable=false) InstallationSchedule schedule;
    @Enumerated(EnumType.STRING) @Column(name="approval_type", nullable=false, length=30) InstallationScheduleApprovalType approvalType;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="actor_user_id", nullable=false) User actor;
    @Column(name="approved_at", nullable=false) LocalDateTime approvedAt;
    @Column(length=500) String reason;
    @PrePersist void pre(){approvedAt=LocalDateTime.now();}
}
