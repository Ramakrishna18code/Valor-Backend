package com.valor.commerce;

import com.valor.assets.AmcContract;
import com.valor.auth.CustomerProfile;
import com.valor.auth.User;
import com.valor.workflow.ServiceRequest;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "Invoice")
@Table(name = "invoices")
@Getter @Setter
class Invoice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "invoice_number", unique = true, length = 40)
    private String invoiceNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfile customer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "service_request_id")
    private ServiceRequest serviceRequest;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "amc_contract_id")
    private AmcContract amcContract;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;
    @Column(name = "description", nullable = false, length = 500)
    private String description;
    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private InvoiceStatus status = InvoiceStatus.ISSUED;
    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate = LocalDate.now();
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist void pre() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void upd() { updatedAt = LocalDateTime.now(); }
}
