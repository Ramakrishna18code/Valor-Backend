package com.valor.commerce;

import com.valor.assets.AmcContract;
import com.valor.auth.CustomerProfile;
import com.valor.auth.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "AmcRenewalRequest")
@Table(name = "amc_renewal_requests")
@Getter @Setter
class AmcRenewalRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "amc_contract_id", nullable = false)
    private AmcContract contract;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfile customer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedBy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "invoice_id")
    private Invoice invoice;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_id")
    private PaymentRecord payment;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private RenewalRequestStatus status = RenewalRequestStatus.REQUESTED;
    @Column(name = "requested_start_date", nullable = false)
    private LocalDate requestedStartDate;
    @Column(name = "requested_end_date", nullable = false)
    private LocalDate requestedEndDate;
    @Column(name = "quoted_amount", precision = 12, scale = 2)
    private BigDecimal quotedAmount;
    @Column(name = "currency", length = 3)
    private String currency;
    @Column(name = "customer_notes", length = 1000)
    private String customerNotes;
    @Column(name = "admin_notes", length = 1000)
    private String adminNotes;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist void pre() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void upd() { updatedAt = LocalDateTime.now(); }
}
