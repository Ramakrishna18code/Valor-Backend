package com.valor.commerce;

import com.valor.assets.*;
import com.valor.auth.*;
import com.valor.workflow.*;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
    Page<PaymentRecord> findByCustomerId(Long customerId, Pageable pageable);
    List<PaymentRecord> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);
    Optional<PaymentRecord> findByInvoiceIdAndStatusIn(Long invoiceId, Collection<PaymentStatus> statuses);
    List<PaymentRecord> findByInvoiceIdOrderByCreatedAtDescIdDesc(Long invoiceId);
    Optional<PaymentRecord> findFirstByServiceRequestIdOrderByCreatedAtDesc(Long serviceRequestId);
    Optional<PaymentRecord> findByRazorpayOrderId(String razorpayOrderId);
    Optional<PaymentRecord> findByRazorpayPaymentId(String razorpayPaymentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentRecord p where p.id=:id")
    Optional<PaymentRecord> lockById(@Param("id") Long id);
}
interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Page<Invoice> findByCustomerId(Long customerId, Pageable pageable);
    Optional<Invoice> findFirstByServiceRequestIdOrderByCreatedAtDesc(Long serviceRequestId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id=:id")
    Optional<Invoice> lockById(@Param("id") Long id);
}
interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    Page<SupportTicket> findByCreatedById(Long userId, Pageable pageable);
}
interface AmcRenewalRequestRepository extends JpaRepository<AmcRenewalRequest, Long> {
    Page<AmcRenewalRequest> findByCustomerId(Long customerId, Pageable pageable);
    boolean existsByContractIdAndStatusIn(Long contractId, Collection<RenewalRequestStatus> statuses);
    Optional<AmcRenewalRequest> findFirstByPaymentIdAndStatusIn(Long paymentId, Collection<RenewalRequestStatus> statuses);
    Optional<AmcRenewalRequest> findFirstByInvoiceIdAndStatusIn(Long invoiceId, Collection<RenewalRequestStatus> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AmcRenewalRequest r join fetch r.contract c join fetch r.customer where r.id=:id")
    Optional<AmcRenewalRequest> lockById(@Param("id") Long id);
}
interface AssetDocumentRepository extends JpaRepository<AssetDocument, Long> {
    List<AssetDocument> findByOwnerTypeAndOwnerIdOrderByCreatedAtAscIdAsc(AssetDocumentOwnerType ownerType, Long ownerId);
}
interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
    List<PaymentRefund> findByPaymentIdOrderByCreatedAtAscIdAsc(Long paymentId);
    Optional<PaymentRefund> findByRazorpayRefundId(String razorpayRefundId);
    boolean existsByPaymentIdAndStatusIn(Long paymentId, Collection<RefundStatus> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRefund r where r.id=:id")
    Optional<PaymentRefund> lockById(@Param("id") Long id);
}
interface RazorpayWebhookEventRepository extends JpaRepository<RazorpayWebhookEvent, Long> {
    boolean existsByEventId(String eventId);
}
interface CommerceCustomerRepository extends JpaRepository<CustomerProfile, Long> {
    Optional<CustomerProfile> findByUserId(Long userId);
}
interface CommerceBuildingRepository extends JpaRepository<Building, Long> {
    @Query("select b from Building b join fetch b.customer c join fetch c.user where b.id=:id")
    Optional<Building> withOwner(@Param("id") Long id);
}
interface CommerceLiftRepository extends JpaRepository<Lift, Long> {
    @Query("select l from Lift l join fetch l.building b join fetch b.customer c join fetch c.user where l.id=:id")
    Optional<Lift> withOwner(@Param("id") Long id);
}
interface CommerceAmcRepository extends JpaRepository<AmcContract, Long> {
    @Query("select a from AmcContract a join fetch a.lift l join fetch l.building b join fetch b.customer c join fetch c.user where a.id=:id")
    Optional<AmcContract> withOwner(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AmcContract a join fetch a.lift l join fetch l.building b join fetch b.customer c join fetch c.user where a.id=:id")
    Optional<AmcContract> lockWithOwner(@Param("id") Long id);
}
interface CommerceRequestRepository extends JpaRepository<ServiceRequest, Long> {
    @Query("select r from ServiceRequest r join fetch r.customer c left join fetch r.lift l left join fetch l.building b left join fetch b.customer bc left join fetch bc.user where r.id=:id")
    Optional<ServiceRequest> withOwner(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ServiceRequest r join fetch r.customer c where r.id=:id")
    Optional<ServiceRequest> lockWithOwner(@Param("id") Long id);
}
interface CommerceAssignmentRepository extends JpaRepository<TechnicianAssignment, Long> {
    boolean existsByRequestIdAndTechnicianUserId(Long requestId, Long userId);
    boolean existsByRequestIdAndTechnicianUserIdAndStatusIn(Long requestId, Long userId, Collection<AssignmentStatus> statuses);
}
interface CommerceReportRepository extends JpaRepository<ServiceReport, Long> {
    Optional<ServiceReport> findByRequestId(Long requestId);
}
