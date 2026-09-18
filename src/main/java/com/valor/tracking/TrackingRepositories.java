package com.valor.tracking;

import com.valor.workflow.*;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface TechnicianLatestLocationRepository extends JpaRepository<TechnicianLatestLocation, Long> {
    Optional<TechnicianLatestLocation> findByServiceRequestId(Long requestId);
}
interface TrackingRequestRepository extends JpaRepository<ServiceRequest, Long> {
    @Query("select r from ServiceRequest r join fetch r.customer c join fetch c.user join fetch r.lift l join fetch l.building where r.id=:id")
    Optional<ServiceRequest> withCustomer(@Param("id") Long id);
}
interface TrackingAssignmentRepository extends JpaRepository<TechnicianAssignment, Long> {
    @Query("select a from TechnicianAssignment a join fetch a.technician t join fetch t.user where a.request.id=:requestId and a.status in (com.valor.workflow.AssignmentStatus.ASSIGNED, com.valor.workflow.AssignmentStatus.ACCEPTED)")
    Optional<TechnicianAssignment> active(@Param("requestId") Long requestId);
}
