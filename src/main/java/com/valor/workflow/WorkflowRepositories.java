package com.valor.workflow;

import com.valor.assets.Lift;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface RequestRepository extends JpaRepository<ServiceRequest, Long> {
    @Query("select r from ServiceRequest r where r.customer.id=:customerId and (:status is null or r.status=:status)")
    Page<ServiceRequest> customerRequests(@Param("customerId") Long customerId,@Param("status") RequestStatus status,Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ServiceRequest r where r.id = :id")
    Optional<ServiceRequest> lockById(@Param("id") Long id);

    @Query("select r from ServiceRequest r where (:status is null or r.status = :status) and (:priority is null or r.priority = :priority)")
    Page<ServiceRequest> search(@Param("status") RequestStatus status, @Param("priority") RequestPriority priority, Pageable page);

    @Query("select r from ServiceRequest r where (:status is null or r.status = :status) and exists (select a.id from TechnicianAssignment a where a.request = r and a.technician.id = :technicianId and a.status in (com.valor.workflow.AssignmentStatus.ASSIGNED, com.valor.workflow.AssignmentStatus.ACCEPTED))")
    Page<ServiceRequest> jobs(@Param("technicianId") Long technicianId, @Param("status") RequestStatus status, Pageable page);
}

interface AssignmentRepository extends JpaRepository<TechnicianAssignment, Long> {
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
