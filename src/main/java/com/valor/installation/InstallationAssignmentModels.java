package com.valor.installation;

import com.valor.auth.TechnicianProfile;
import com.valor.auth.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

enum InstallationAssignmentStatus { ASSIGNED, ACCEPTED, DECLINED, CANCELLED }

@Entity @Table(name="installation_assignments") @Getter @Setter
class InstallationAssignment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="installation_request_id") InstallationRequest request;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="technician_profile_id") TechnicianProfile technician;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="assigned_by_user_id") User assignedBy;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) InstallationAssignmentStatus status=InstallationAssignmentStatus.ASSIGNED;
 @Column(name="assigned_at",nullable=false) LocalDateTime assignedAt=LocalDateTime.now();
 @Column(name="responded_at") LocalDateTime respondedAt; @Column(name="cancelled_at") LocalDateTime cancelledAt;
 @Column(length=2000) String notes; @Version @Column(nullable=false) Long version;
}
@Entity @Table(name="installation_assignment_history") @Getter @Setter
class InstallationAssignmentHistory { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="assignment_id") InstallationAssignment assignment; @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="changed_by_user_id") User changedBy; @Enumerated(EnumType.STRING) @Column(name="from_status",length=20) InstallationAssignmentStatus fromStatus; @Enumerated(EnumType.STRING) @Column(name="to_status",nullable=false,length=20) InstallationAssignmentStatus toStatus; @Column(length=2000) String notes; @Column(name="changed_at",nullable=false) LocalDateTime changedAt=LocalDateTime.now(); }

interface InstallationAssignmentRepository extends org.springframework.data.jpa.repository.JpaRepository<InstallationAssignment,Long> { java.util.List<InstallationAssignment> findByRequestIdOrderByAssignedAtDescIdDesc(Long id); java.util.List<InstallationAssignment> findByTechnicianIdOrderByAssignedAtDescIdDesc(Long id); java.util.Optional<InstallationAssignment> findByIdAndTechnicianId(Long id,Long technicianId); }
interface InstallationAssignmentHistoryRepository extends org.springframework.data.jpa.repository.JpaRepository<InstallationAssignmentHistory,Long> { java.util.List<InstallationAssignmentHistory> findByAssignmentIdOrderByChangedAtAscIdAsc(Long id); }
