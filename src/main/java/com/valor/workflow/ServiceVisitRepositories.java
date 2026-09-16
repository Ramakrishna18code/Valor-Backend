package com.valor.workflow;

import com.valor.auth.TechnicianProfile;
import jakarta.persistence.LockModeType;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface ServiceVisitRepository extends JpaRepository<ServiceVisit, Long> {
    @Query("select v from ServiceVisit v join fetch v.request r join fetch r.customer c join fetch r.lift l join fetch v.technician t join fetch t.user u where (:fromDate is null or v.scheduledDate >= :fromDate) and (:toDate is null or v.scheduledDate <= :toDate) and (:technicianId is null or t.id = :technicianId) and (:requestId is null or r.id = :requestId) and (:status is null or v.status = :status)")
    Page<ServiceVisit> search(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
            @Param("technicianId") Long technicianId, @Param("requestId") Long requestId, @Param("status") VisitStatus status, Pageable page);
    @Query("select v from ServiceVisit v join fetch v.request r join fetch r.customer c join fetch r.lift l join fetch v.technician t join fetch t.user u where v.id = :id")
    Optional<ServiceVisit> detail(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ServiceVisit v where v.id = :id")
    Optional<ServiceVisit> lockById(@Param("id") Long id);
    @Query("select v from ServiceVisit v where v.request.id = :requestId and v.status in :statuses order by v.scheduledDate, v.startTime")
    List<ServiceVisit> activeForRequest(@Param("requestId") Long requestId, @Param("statuses") Collection<VisitStatus> statuses);
    @Query("select v from ServiceVisit v where v.technician.id = :technicianId and v.scheduledDate = :date and v.status in :statuses and v.startTime < :endTime and v.endTime > :startTime and (:excludeId is null or v.id <> :excludeId)")
    List<ServiceVisit> overlaps(@Param("technicianId") Long technicianId, @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime,
            @Param("statuses") Collection<VisitStatus> statuses, @Param("excludeId") Long excludeId);
    @Query("select v from ServiceVisit v join fetch v.request r join fetch r.customer c join fetch r.lift l join fetch v.technician t join fetch t.user u where t.id = :technicianId and (:fromDate is null or v.scheduledDate >= :fromDate) and (:toDate is null or v.scheduledDate <= :toDate) and (:status is null or v.status = :status)")
    Page<ServiceVisit> technicianVisits(@Param("technicianId") Long technicianId, @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate, @Param("status") VisitStatus status, Pageable page);
    @Query("select v from ServiceVisit v join fetch v.request r join fetch r.customer c join fetch r.lift l join fetch v.technician t join fetch t.user u where r.customer.id = :customerId and (:requestId is null or r.id = :requestId) and (:status is null or v.status = :status)")
    Page<ServiceVisit> customerVisits(@Param("customerId") Long customerId, @Param("requestId") Long requestId,
            @Param("status") VisitStatus status, Pageable page);
}

interface VisitTechnicianRepository extends Repository<TechnicianProfile, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TechnicianProfile t join fetch t.user u where t.id = :id")
    Optional<TechnicianProfile> lockById(@Param("id") Long id);
}

interface VisitHistoryRepository extends JpaRepository<VisitHistory, Long> {
    List<VisitHistory> findByVisitIdOrderByChangedAtAscIdAsc(Long visitId);
}

interface VisitChangeRequestRepository extends JpaRepository<VisitChangeRequest, Long> {
    @Query(value = "select r from VisitChangeRequest r join fetch r.request q join fetch q.customer c join fetch q.lift l join fetch r.requestedTechnician t join fetch t.user u where (:type is null or r.type = :type) and (:status is null or r.status = :status)",
            countQuery = "select count(r) from VisitChangeRequest r where (:type is null or r.type = :type) and (:status is null or r.status = :status)")
    Page<VisitChangeRequest> search(@Param("type") VisitChangeRequestType type, @Param("status") VisitChangeRequestStatus status, Pageable page);
    @Query("select r from VisitChangeRequest r join fetch r.request q join fetch q.customer c join fetch q.lift l join fetch r.requestedTechnician t join fetch t.user u where r.id = :id")
    Optional<VisitChangeRequest> detail(@Param("id") Long id);
    boolean existsByVisitIdAndTypeAndStatus(Long visitId, VisitChangeRequestType type, VisitChangeRequestStatus status);
}
