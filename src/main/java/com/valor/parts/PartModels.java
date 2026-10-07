package com.valor.parts;

import com.valor.auth.TechnicianProfile;
import com.valor.workflow.ServiceRequest;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

@Entity
@Table(name = "part_categories", uniqueConstraints = @UniqueConstraint(name = "uk_part_category_name", columnNames = "name"))
class PartCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 120) String name;
    @Column(length = 500) String description;
    @Column(nullable = false, name = "is_active") boolean active = true;
    @Column(nullable = false) LocalDateTime createdAt;
    @Column(nullable = false) LocalDateTime updatedAt;
    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "part_items", uniqueConstraints = @UniqueConstraint(name = "uk_part_item_sku", columnNames = "sku"))
class PartItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "category_id", nullable = false) PartCategory category;
    @Column(nullable = false, length = 180) String name;
    @Column(nullable = false, length = 80) String sku;
    @Column(length = 1000) String description;
    @Column(nullable = false, length = 40) String unit;
    @Column(nullable = false) int quantityOnHand;
    @Column(nullable = false) int reorderThreshold;
    @Column(nullable = false, name = "is_active") boolean active = true;
    @Column(name = "compatibility_metadata", length = 500) String compatibilityMetadata;
    @Version long version;
    @Column(nullable = false) LocalDateTime createdAt;
    @Column(nullable = false) LocalDateTime updatedAt;
    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}

enum StockMovementType { RESTOCK, ADJUSTMENT, RESERVE, RELEASE, ISSUE, RETURN, CONSUME }
enum PartRequestStatus { DRAFT, SUBMITTED, UNDER_REVIEW, REJECTED, APPROVED, RESERVED, READY, ISSUED, CONSUMED, CANCELLED }

@Entity
@Table(name = "part_stock_movements")
class PartStockMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "part_item_id", nullable = false) PartItem item;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) StockMovementType movementType;
    @Column(nullable = false) int quantity;
    @Column(length = 500) String reason;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "service_request_id") ServiceRequest serviceRequest;
    @Column(name = "actor_user_id") Long actorUserId;
    @Column(nullable = false) LocalDateTime createdAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "service_request_part_requests")
class ServiceRequestPartRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_request_id", nullable = false) ServiceRequest serviceRequest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "technician_profile_id", nullable = false) TechnicianProfile technician;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) PartRequestStatus status = PartRequestStatus.DRAFT;
    @Column(length = 1000) String notes;
    @Column(nullable = false) LocalDateTime createdAt;
    LocalDateTime submittedAt; LocalDateTime approvedAt; LocalDateTime rejectedAt; LocalDateTime reservedAt; LocalDateTime issuedAt; LocalDateTime consumedAt; LocalDateTime cancelledAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "service_request_part_lines", uniqueConstraints = @UniqueConstraint(name = "uk_part_request_item", columnNames = {"request_id", "part_item_id"}))
class ServiceRequestPartLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "request_id", nullable = false) ServiceRequestPartRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "part_item_id", nullable = false) PartItem item;
    @Column(nullable = false) int requestedQuantity;
    @Column(nullable = false) int approvedQuantity;
    @Column(nullable = false) int issuedQuantity;
}

@Entity
@Table(name = "part_request_events")
class PartRequestEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "request_id", nullable = false) ServiceRequestPartRequest request;
    @Enumerated(EnumType.STRING) @Column(name = "previous_status", length = 20) PartRequestStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", nullable = false, length = 20) PartRequestStatus newStatus;
    @Column(name = "actor_user_id") Long actorUserId;
    @Column(length = 1000) String reason;
    @Column(nullable = false) LocalDateTime createdAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}

interface PartCategoryRepository extends JpaRepository<PartCategory, Long> { Optional<PartCategory> findByNameIgnoreCase(String name); }
interface PartItemRepository extends JpaRepository<PartItem, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @org.springframework.data.jpa.repository.Query("select p from PartItem p join fetch p.category where p.id=:id") Optional<PartItem> lockById(@Param("id") Long id);
    @org.springframework.data.jpa.repository.Query("select p from PartItem p join fetch p.category c where (:categoryId is null or c.id=:categoryId) and (:includeInactive=true or (p.active=true and c.active=true)) and (:query is null or lower(p.name) like lower(concat('%', :query, '%')) or lower(p.sku) like lower(concat('%', :query, '%')))")
    Page<PartItem> search(@Param("query") String query, @Param("categoryId") Long categoryId, @Param("includeInactive") boolean includeInactive, Pageable pageable);
    @org.springframework.data.jpa.repository.Query("select p from PartItem p join fetch p.category where p.id=:id") Optional<PartItem> findWithCategoryById(@Param("id") Long id);
    boolean existsBySkuIgnoreCase(String sku);
    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);
}
interface PartMovementRepository extends JpaRepository<PartStockMovement, Long> { List<PartStockMovement> findByItemIdOrderByCreatedAtDesc(Long itemId); }
interface PartRequestRepository extends JpaRepository<ServiceRequestPartRequest, Long> {
    @org.springframework.data.jpa.repository.Query("select r from ServiceRequestPartRequest r where r.technician.user.id=:userId order by r.createdAt desc") Page<ServiceRequestPartRequest> findByTechnicianUserId(@Param("userId") Long userId, Pageable pageable);
    Page<ServiceRequestPartRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<ServiceRequestPartRequest> findByServiceRequestIdOrderByCreatedAtDesc(Long serviceRequestId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @org.springframework.data.jpa.repository.Query("select r from ServiceRequestPartRequest r where r.id=:id") Optional<ServiceRequestPartRequest> lockById(@Param("id") Long id);
}
interface PartLineRepository extends JpaRepository<ServiceRequestPartLine, Long> { List<ServiceRequestPartLine> findByRequestId(Long id); }
interface PartEventRepository extends JpaRepository<PartRequestEvent, Long> { List<PartRequestEvent> findByRequestIdOrderByCreatedAtAsc(Long id); }
interface PartsTechnicianRepository extends JpaRepository<TechnicianProfile, Long> { Optional<TechnicianProfile> findByUserId(Long userId); }
interface PartsServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {}
