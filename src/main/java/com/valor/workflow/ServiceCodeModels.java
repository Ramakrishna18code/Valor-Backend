package com.valor.workflow;

import com.valor.assets.Lift;
import com.valor.auth.CustomerProfile;
import com.valor.auth.TechnicianProfile;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Entity
@Table(name = "lift_service_codes")
class LiftServiceCode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lift_id", unique = true, nullable = false) Lift lift;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_profile_id", nullable = false) CustomerProfile customer;
    @Column(name = "code_hash", nullable = false, length = 255) String codeHash;
    @Column(name = "code_ciphertext", nullable = false, length = 512) String codeCiphertext;
    @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;
    @PrePersist @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "service_code_verifications")
class ServiceCodeVerification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_request_id", nullable = false) ServiceRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lift_service_code_id", nullable = false) LiftServiceCode code;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "technician_id", nullable = false) TechnicianProfile technician;
    @Enumerated(EnumType.STRING) @Column(name = "action", nullable = false, length = 16) ServiceCodeAction action;
    @Column(name = "attempts", nullable = false) short attempts;
    @Column(name = "locked_until") LocalDateTime lockedUntil;
    @Column(name = "verified_at") LocalDateTime verifiedAt;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}

enum ServiceCodeAction { START, COMPLETE }

interface LiftServiceCodeRepository extends JpaRepository<LiftServiceCode, Long> {
    Optional<LiftServiceCode> findByLiftId(Long liftId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LiftServiceCode c join fetch c.lift l join fetch l.building b join fetch b.customer where c.lift.id=:liftId")
    Optional<LiftServiceCode> lockByLiftId(@Param("liftId") Long liftId);
}

interface ServiceCodeVerificationRepository extends JpaRepository<ServiceCodeVerification, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ServiceCodeVerification v where v.request.id=:requestId and v.technician.id=:technicianId and v.action=:action")
    Optional<ServiceCodeVerification> lock(@Param("requestId") Long requestId, @Param("technicianId") Long technicianId, @Param("action") ServiceCodeAction action);
    Optional<ServiceCodeVerification> findTopByRequestIdAndActionOrderByCreatedAtDescIdDesc(Long requestId, ServiceCodeAction action);
}
