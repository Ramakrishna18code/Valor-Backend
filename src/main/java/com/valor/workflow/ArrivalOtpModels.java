package com.valor.workflow;

import com.valor.auth.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.LockModeType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Entity
@Table(name = "arrival_otps")
class ArrivalOtp {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_request_id") ServiceRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_profile_id") CustomerProfile customer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "requested_by_technician_id") TechnicianProfile technician;
    @Column(name = "otp_hash", nullable = false, length = 255) String otpHash;
    @Column(name = "attempts_remaining", nullable = false) short attemptsRemaining = 3;
    @Column(name = "max_attempts", nullable = false) short maxAttempts = 3;
    @Column(name = "expires_at", nullable = false) LocalDateTime expiresAt;
    @Column(name = "locked_until") LocalDateTime lockedUntil;
    @Column(name = "verified_at") LocalDateTime verifiedAt;
    @Column(name = "customer_visible_code", length = 16) String customerVisibleCode;
    @Column(name = "customer_visible_until") LocalDateTime customerVisibleUntil;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}

interface ArrivalOtpRepository extends JpaRepository<ArrivalOtp, Long> {
    @Query("select o from ArrivalOtp o where o.id=:id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ArrivalOtp> lockById(@Param("id") Long id);
    Optional<ArrivalOtp> findTopByRequestIdOrderByCreatedAtDesc(Long requestId);
}
