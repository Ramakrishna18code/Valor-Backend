package com.valor.workflow;

import com.valor.auth.TechnicianProfile;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

enum ChecklistInputType { CHECKBOX, TEXT, NUMBER, PHOTO_NOTE }
enum JobChecklistStatus { IN_PROGRESS, COMPLETED }
enum ChecklistSnapshotQuality { VERIFIED, RECONSTRUCTED }

@Entity
@Table(name = "checklist_templates")
class ChecklistTemplate {
    public Long getId() { return id; }
    public String getName() { return name; }
    public int getVersion() { return version; }
    public void incrementVersion() { version++; }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 160) String name;
    @Column(length = 1000) String description;
    @Column(nullable = false) boolean active = true;
    @Column(nullable = false) int version = 1;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "checklist_template_service_types", joinColumns = @JoinColumn(name = "template_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 40)
    Set<WorkflowServiceType> serviceTypes = new LinkedHashSet<>();
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "checklist_items")
class ChecklistItem {
    public Long getId() { return id; }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "template_id") ChecklistTemplate template;
    @Column(nullable = false, length = 255) String label;
    @Column(length = 1000) String description;
    @Column(nullable = false) boolean required = true;
    @Column(name = "sort_order", nullable = false) int sortOrder;
    @Enumerated(EnumType.STRING) @Column(name = "input_type", nullable = false, length = 30) ChecklistInputType inputType = ChecklistInputType.CHECKBOX;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "job_checklists")
class JobChecklist {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_request_id") ServiceRequest request;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "service_visit_id") ServiceVisit visit;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "template_id") ChecklistTemplate template;
    @Column(name = "template_version", nullable = false) int templateVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) JobChecklistStatus status = JobChecklistStatus.IN_PROGRESS;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "job_checklist_items")
class JobChecklistItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_checklist_id", nullable = false)
    JobChecklist checklist;
    @Column(name = "source_item_id") Long sourceItemId;
    @Column(nullable = false, length = 255) String label;
    @Column(length = 1000) String description;
    @Column(nullable = false) boolean required = true;
    @Column(name = "sort_order", nullable = false) int sortOrder;
    @Enumerated(EnumType.STRING)
    @Column(name = "input_type", nullable = false, length = 30)
    ChecklistInputType inputType = ChecklistInputType.CHECKBOX;
    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_quality", nullable = false, length = 20)
    ChecklistSnapshotQuality snapshotQuality = ChecklistSnapshotQuality.RECONSTRUCTED;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "checklist_responses")
class ChecklistResponse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "job_checklist_id") JobChecklist checklist;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "item_id") ChecklistItem item;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "technician_profile_id") TechnicianProfile technician;
    @Column(name = "job_checklist_item_id") Long jobChecklistItemId;
    Boolean checked;
    @Column(name = "value_text", length = 2000) String valueText;
    @Column(name = "responded_at", nullable = false) LocalDateTime respondedAt;
}

interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, Long> {
    @org.springframework.data.jpa.repository.Query("select t from ChecklistTemplate t where t.active=true and (:type member of t.serviceTypes or t.serviceTypes is empty) order by t.version desc, t.id desc")
    List<ChecklistTemplate> activeFor(@Param("type") WorkflowServiceType type, Pageable page);
}

interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {
    List<ChecklistItem> findByTemplateIdOrderBySortOrderAscIdAsc(Long templateId);
}

interface JobChecklistRepository extends JpaRepository<JobChecklist, Long> {
    Optional<JobChecklist> findByRequestId(Long requestId);
    @org.springframework.data.jpa.repository.Query("select j from JobChecklist j where (:requestId is null or j.request.id=:requestId) and (:status is null or j.status=:status) order by j.updatedAt desc, j.id desc")
    Page<JobChecklist> search(@Param("requestId") Long requestId, @Param("status") JobChecklistStatus status, Pageable pageable);
}

interface ChecklistResponseRepository extends JpaRepository<ChecklistResponse, Long> {
    List<ChecklistResponse> findByChecklistId(Long checklistId);
    Optional<ChecklistResponse> findByChecklistIdAndItemId(Long checklistId, Long itemId);
}

interface JobChecklistItemRepository extends JpaRepository<JobChecklistItem, Long> {
    List<JobChecklistItem> findByChecklistIdOrderBySortOrderAscIdAsc(Long checklistId);
}
