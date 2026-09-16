package com.valor.commerce;

import com.valor.auth.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "PaymentRefund")
@Table(name = "payment_refunds")
@Getter @Setter
class PaymentRefund {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_id", nullable = false)
    private PaymentRecord payment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedBy;
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private RefundStatus status = RefundStatus.REQUESTED;
    @Column(name = "razorpay_refund_id", length = 80)
    private String razorpayRefundId;
    @Column(name = "gateway_status", length = 60)
    private String gatewayStatus;
    @Column(name = "reason", length = 500)
    private String reason;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist void pre() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void upd() { updatedAt = LocalDateTime.now(); }
}
