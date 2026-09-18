package com.valor.workflow;

import com.valor.auth.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

@Entity
@Table(name = "completion_otps")
class CompletionOtp {
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
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}

interface CompletionOtpRepository extends JpaRepository<CompletionOtp, Long> {
    @org.springframework.data.jpa.repository.Query("select o from CompletionOtp o where o.id=:id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CompletionOtp> lockById(@Param("id") Long id);
    Optional<CompletionOtp> findTopByRequestIdAndVerifiedAtIsNotNullOrderByVerifiedAtDesc(Long requestId);
    Optional<CompletionOtp> findTopByRequestIdOrderByCreatedAtDesc(Long requestId);
}

interface CompletionOtpSender {
    void sendCompletionOtp(ServiceRequest request, String code);
}

@org.springframework.stereotype.Component
class NoopCompletionOtpSender implements CompletionOtpSender {
    public void sendCompletionOtp(ServiceRequest request, String code) {
        // Provider activation is intentionally deferred; never log or return the code.
    }
}
