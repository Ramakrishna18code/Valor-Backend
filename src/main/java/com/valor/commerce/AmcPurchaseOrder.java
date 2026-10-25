package com.valor.commerce;

import com.valor.assets.AmcContract;
import com.valor.assets.Lift;
import com.valor.auth.CustomerProfile;
import com.valor.auth.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "AmcPurchaseOrder")
@Table(name = "amc_purchase_orders")
@Getter @Setter
class AmcPurchaseOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfile customer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lift_id", nullable = false)
    private Lift lift;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private PaymentRecord payment;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "invoice_id", unique = true)
    private Invoice invoice;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "amc_contract_id", unique = true)
    private AmcContract contract;
    @Column(name = "plan_code", nullable = false, length = 40)
    private String planCode;
    @Column(name = "plan_name", nullable = false, length = 80)
    private String planName;
    @Column(name = "base_months", nullable = false)
    private Integer baseMonths;
    @Column(name = "bonus_months", nullable = false)
    private Integer bonusMonths;
    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private AmcPurchaseStatus status = AmcPurchaseStatus.PAYMENT_PENDING;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist void pre() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void upd() { updatedAt = LocalDateTime.now(); }
}
