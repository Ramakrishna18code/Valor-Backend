package com.valor.installation;

import com.valor.auth.TechnicianProfile;
import com.valor.auth.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

enum InspectionStatus { REQUESTED, SCHEDULED, ASSIGNED, IN_PROGRESS, SUBMITTED, REVIEW_REQUIRED, APPROVED, REWORK_REQUIRED, CANCELLED, RESCHEDULE_REQUIRED }
@Entity @Table(name="installation_inspections", uniqueConstraints=@UniqueConstraint(name="uk_installation_inspection_request", columnNames="installation_request_id")) @Getter @Setter
class InstallationInspection {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @OneToOne(optional=false, fetch=FetchType.LAZY) @JoinColumn(name="installation_request_id", nullable=false) InstallationRequest request;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) InspectionStatus status=InspectionStatus.REQUESTED;
 @Column(name="scheduled_at") LocalDateTime scheduledAt; @Column(name="scheduled_timezone",length=80) String scheduledTimezone;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="assigned_technician_id") TechnicianProfile assignedTechnician;
 @Column(name="visit_started_at") LocalDateTime visitStartedAt; @Column(name="submitted_at") LocalDateTime submittedAt;
 @Column(length=40) String conclusion; @Column(columnDefinition="TEXT") String measurements, findings, siteConstraints, readinessObservations, issues, risks, recommendedActions, reviewNotes;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reviewer_user_id") User reviewer; @Column(name="reviewed_at") LocalDateTime reviewedAt;
 @Version @Column(nullable=false) Long version;
 @Column(name="created_at",nullable=false) LocalDateTime createdAt; @Column(name="updated_at",nullable=false) LocalDateTime updatedAt;
 @PrePersist void p(){createdAt=LocalDateTime.now();updatedAt=createdAt;} @PreUpdate void u(){updatedAt=LocalDateTime.now();}
}
@Entity @Table(name="installation_inspection_history") @Getter @Setter class InstallationInspectionHistory {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="inspection_id") InstallationInspection inspection;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="changed_by_user_id") User changedBy; @Enumerated(EnumType.STRING) @Column(name="from_status",length=40) InspectionStatus fromStatus; @Enumerated(EnumType.STRING) @Column(name="to_status",nullable=false,length=40) InspectionStatus toStatus;
 @Column(length=2000) String notes; @Column(name="changed_at",nullable=false) LocalDateTime changedAt;
 @PrePersist void p(){changedAt=LocalDateTime.now();}
}
@Entity @Table(name="installation_inspection_evidence") @Getter @Setter class InstallationInspectionEvidence {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="inspection_id") InstallationInspection inspection; @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="referenced_by_user_id") User referencedBy;
 @Column(name="document_type",nullable=false,length=80) String documentType; @Column(name="original_filename",nullable=false,length=255) String originalFilename; @Column(name="content_type",nullable=false,length=100) String contentType; @Column(name="storage_key",nullable=false,length=500,unique=true) String storageKey; @Column(name="created_at",nullable=false) LocalDateTime createdAt;
 @PrePersist void p(){createdAt=LocalDateTime.now();}
}
interface InstallationInspectionRepository extends org.springframework.data.jpa.repository.JpaRepository<InstallationInspection,Long> {
 java.util.Optional<InstallationInspection> findByRequestId(Long requestId);
 java.util.Optional<InstallationInspection> findByIdAndAssignedTechnicianId(Long id,Long technicianId);
 @org.springframework.data.jpa.repository.Query("select i from InstallationInspection i join fetch i.request r left join fetch i.assignedTechnician t where (:status is null or i.status=:status) and (:technicianId is null or t.id=:technicianId) and (:fromDate is null or i.scheduledAt >= :fromDate) and (:toDate is null or i.scheduledAt < :toDate) order by i.createdAt desc, i.id desc")
 org.springframework.data.domain.Page<InstallationInspection> search(@org.springframework.data.repository.query.Param("status") InspectionStatus status,@org.springframework.data.repository.query.Param("technicianId") Long technicianId,@org.springframework.data.repository.query.Param("fromDate") java.time.LocalDateTime fromDate,@org.springframework.data.repository.query.Param("toDate") java.time.LocalDateTime toDate,org.springframework.data.domain.Pageable pageable);
 @org.springframework.data.jpa.repository.Query("select i from InstallationInspection i join fetch i.request r left join fetch i.assignedTechnician t where t.id=:technicianId and (:status is null or i.status=:status) order by i.createdAt desc, i.id desc")
 org.springframework.data.domain.Page<InstallationInspection> assignedTo(@org.springframework.data.repository.query.Param("technicianId") Long technicianId,@org.springframework.data.repository.query.Param("status") InspectionStatus status,org.springframework.data.domain.Pageable pageable);
}
interface InspectionHistoryRepository extends org.springframework.data.jpa.repository.JpaRepository<InstallationInspectionHistory,Long> { java.util.List<InstallationInspectionHistory> findByInspectionIdOrderByChangedAtAscIdAsc(Long id); }
interface InspectionEvidenceRepository extends org.springframework.data.jpa.repository.JpaRepository<InstallationInspectionEvidence,Long> { java.util.List<InstallationInspectionEvidence> findByInspectionIdOrderByCreatedAtAscIdAsc(Long id); }
