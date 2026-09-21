package com.valor.workflow;

import com.valor.assets.Lift;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface RequestRepository extends JpaRepository<ServiceRequest, Long> {
    @Query("select r from ServiceRequest r where r.customer.id=:customerId and (:status is null or r.status=:status) and (:fromDate is null or r.serviceRequestedAt>=:fromDate) and (:toDate is null or r.serviceRequestedAt<:toDate)")
    Page<ServiceRequest> customerRequests(@Param("customerId") Long customerId,@Param("status") RequestStatus status,@Param("fromDate") java.time.LocalDateTime from,@Param("toDate") java.time.LocalDateTime to,Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ServiceRequest r where r.id = :id")
    Optional<ServiceRequest> lockById(@Param("id") Long id);

    @Query("select r from ServiceRequest r where (:status is null or r.status = :status) and (:priority is null or r.priority = :priority)")
    Page<ServiceRequest> search(@Param("status") RequestStatus status, @Param("priority") RequestPriority priority, Pageable page);

    @Query("select r from ServiceRequest r where (:status is null or r.status = :status) and (exists (select a.id from TechnicianAssignment a where a.request = r and a.technician.id = :technicianId and a.status in (com.valor.workflow.AssignmentStatus.ASSIGNED, com.valor.workflow.AssignmentStatus.ACCEPTED)) or (r.status in (com.valor.workflow.RequestStatus.COMPLETED, com.valor.workflow.RequestStatus.CANCELLED) and exists (select h.id from TechnicianAssignment h where h.request = r and h.technician.id = :technicianId)))")
    Page<ServiceRequest> jobs(@Param("technicianId") Long technicianId, @Param("status") RequestStatus status, Pageable page);
    @Query("select count(r) from ServiceRequest r where exists (select a.id from TechnicianAssignment a where a.request = r and a.technician.id = :technicianId and a.status in (com.valor.workflow.AssignmentStatus.ASSIGNED, com.valor.workflow.AssignmentStatus.ACCEPTED))")
    long activeJobCount(@Param("technicianId") Long technicianId);
    @Query("select count(r) from ServiceRequest r where r.status = :status and exists (select a.id from TechnicianAssignment a where a.request = r and a.technician.id = :technicianId)")
    long assignedStatusCount(@Param("technicianId") Long technicianId, @Param("status") RequestStatus status);
    @Query("select count(r) from ServiceRequest r where r.priority = com.valor.workflow.RequestPriority.EMERGENCY and exists (select a.id from TechnicianAssignment a where a.request = r and a.technician.id = :technicianId and a.status in (com.valor.workflow.AssignmentStatus.ASSIGNED, com.valor.workflow.AssignmentStatus.ACCEPTED))")
    long activeEmergencyCount(@Param("technicianId") Long technicianId);
    @Query("select count(r) from ServiceRequest r where r.status = com.valor.workflow.RequestStatus.COMPLETED and r.completedAt >= :from and r.completedAt < :to and exists (select a.id from TechnicianAssignment a where a.request = r and a.technician.id = :technicianId)")
    long completedBetween(@Param("technicianId") Long technicianId, @Param("from") java.time.LocalDateTime from, @Param("to") java.time.LocalDateTime to);
}

interface AssignmentRepository extends JpaRepository<TechnicianAssignment, Long> {
    boolean existsByRequestIdAndTechnicianId(Long requestId, Long technicianId);
    @Query("select a from TechnicianAssignment a join fetch a.technician t join fetch t.user where a.request.id = :requestId and a.status in (com.valor.workflow.AssignmentStatus.ASSIGNED, com.valor.workflow.AssignmentStatus.ACCEPTED)")
    Optional<TechnicianAssignment> active(@Param("requestId") Long requestId);
}

// No update/delete repository operations are exposed for immutable history.
interface HistoryRepository extends Repository<ServiceStatusHistory, Long> {
    ServiceStatusHistory save(ServiceStatusHistory event);
    List<ServiceStatusHistory> findByRequestIdOrderByChangedAtAscIdAsc(Long id);
}

interface ReportRepository extends JpaRepository<ServiceReport, Long> {
    Optional<ServiceReport> findByRequestId(Long id);
}

interface WorkflowLiftRepository extends Repository<Lift, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lift l join fetch l.building b join fetch b.customer c where l.id = :id")
    Optional<Lift> forIntake(@Param("id") Long id);
}
