package com.valor.workflow;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface ReassignmentRepository extends JpaRepository<TechnicianReassignmentRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from TechnicianReassignmentRequest r where r.id=:id") Optional<TechnicianReassignmentRequest> lockById(@Param("id") Long id);
    Optional<TechnicianReassignmentRequest> findByServiceRequestIdAndStatus(Long requestId, ReassignmentStatus status);
    Page<TechnicianReassignmentRequest> findByStatus(ReassignmentStatus status, Pageable pageable);
    Page<TechnicianReassignmentRequest> findByServiceRequestIdAndRequestingTechnicianId(Long requestId, Long technicianId, Pageable pageable);
}
