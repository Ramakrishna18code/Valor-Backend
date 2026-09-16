package com.valor.commerce;

import com.valor.assets.AmcContract;
import com.valor.auth.CustomerProfile;
import com.valor.auth.User;
import com.valor.workflow.ServiceRequest;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "PaymentRecord")
@Table(name = "payment_records")
@Getter @Setter
class PaymentRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfile customer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "service_request_id")
    private ServiceRequest serviceRequest;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "amc_contract_id")
    private AmcContract amcContract;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "invoice_id")
    private Invoice invoice;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "purpose", nullable = false, length = 30)
    private PaymentPurpose purpose = PaymentPurpose.OTHER;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.PENDING;
    @Column(name = "provider_reference", length = 120)
    private String providerReference;
    @Column(name = "razorpay_order_id", length = 80)
    private String razorpayOrderId;
    @Column(name = "razorpay_payment_id", length = 80)
    private String razorpayPaymentId;
    @Column(name = "razorpay_payment_link_id", length = 80)
    private String razorpayPaymentLinkId;
    @Column(name = "gateway_status", length = 60)
    private String gatewayStatus;
    @Column(name = "gateway_synced_at")
    private LocalDateTime gatewaySyncedAt;
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist void pre() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void upd() { updatedAt = LocalDateTime.now(); }
}
