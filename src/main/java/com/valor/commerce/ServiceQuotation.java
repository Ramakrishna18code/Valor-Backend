package com.valor.commerce;

import com.valor.auth.CustomerProfile;
import com.valor.auth.User;
import com.valor.workflow.ServiceRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OrderBy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "ServiceQuotation")
@Table(name = "service_quotations")
@Getter
@Setter
class ServiceQuotation {
    @Column(name="version_number",nullable=false) private int versionNumber=1;
    @Column(name="supersedes_quotation_id") private Long supersedesQuotationId;
    @Column(name="revision_kind",nullable=false,length=20) private String revisionKind="ORIGINAL";
    @Column(name="release_status",nullable=false,length=20) private String releaseStatus="RELEASED";
    @Column(name="released_at") private LocalDateTime releasedAt;
    @Column(name="revision_reason",length=1000) private String revisionReason;
    @Column(name="approved_terms_hash",length=64) private String approvedTermsHash;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quotation_number", nullable = false, unique = true, length = 40)
    private String quotationNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfile customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest serviceRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", unique = true)
    private Invoice invoice;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<ServiceQuotationItem> items = new ArrayList<>();

    @Column(name = "pricing_code", length = 80)
    private String pricingCode;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceQuotationStatus status = ServiceQuotationStatus.ISSUED;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @jakarta.persistence.PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @jakarta.persistence.PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

enum ServiceQuotationStatus {
    ISSUED,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    CANCELLED
}
