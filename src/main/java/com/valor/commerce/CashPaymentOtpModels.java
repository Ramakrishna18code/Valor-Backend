package com.valor.commerce;

import com.valor.auth.CustomerProfile;
import com.valor.auth.User;
import com.valor.workflow.ServiceRequest;
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
@Table(name = "cash_payment_otps")
class CashPaymentOtp {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_id") PaymentRecord payment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "invoice_id") Invoice invoice;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "service_request_id") ServiceRequest serviceRequest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_profile_id") CustomerProfile customer;
    @Column(name = "otp_hash", nullable = false, length = 255) String otpHash;
    @Column(name = "attempts_remaining", nullable = false) short attemptsRemaining = 3;
    @Column(name = "max_attempts", nullable = false) short maxAttempts = 3;
    @Column(name = "expires_at", nullable = false) LocalDateTime expiresAt;
    @Column(name = "locked_until") LocalDateTime lockedUntil;
    @Column(name = "verified_at") LocalDateTime verifiedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "verified_by_user_id") User verifiedBy;
    @Column(name = "customer_visible_code", length = 16) String customerVisibleCode;
    @Column(name = "customer_visible_until") LocalDateTime customerVisibleUntil;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}

interface CashPaymentOtpRepository extends JpaRepository<CashPaymentOtp, Long> {
    @Query("select o from CashPaymentOtp o where o.id=:id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CashPaymentOtp> lockById(@Param("id") Long id);
    Optional<CashPaymentOtp> findTopByPaymentIdOrderByCreatedAtDesc(Long paymentId);
}
