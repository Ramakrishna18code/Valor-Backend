package com.valor.commerce;

import com.valor.workflow.ServiceRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ServiceQuotationRepository extends JpaRepository<ServiceQuotation, Long> {
    @Query("select q from ServiceQuotation q where q.customer.user.id = :userId and q.releaseStatus = 'RELEASED'")
    Page<ServiceQuotation> findForCustomer(@Param("userId") Long userId, Pageable pageable);

    List<ServiceQuotation> findByServiceRequestIdOrderByCreatedAtDescIdDesc(Long serviceRequestId);

    Page<ServiceQuotation> findByStatus(ServiceQuotationStatus status, Pageable pageable);

    boolean existsByServiceRequestIdAndStatus(Long serviceRequestId, ServiceQuotationStatus status);

    @Query("select q from ServiceQuotation q where q.id = :id and q.customer.user.id = :userId and q.releaseStatus = 'RELEASED'")
    Optional<ServiceQuotation> findForCustomerById(@Param("id") Long id, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from ServiceQuotation q where q.id = :id")
    Optional<ServiceQuotation> lockById(@Param("id") Long id);
}

interface QuotationServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ServiceRequest r join fetch r.customer c join fetch c.user where r.id = :id")
    Optional<ServiceRequest> lockForQuotation(@Param("id") Long id);
}
