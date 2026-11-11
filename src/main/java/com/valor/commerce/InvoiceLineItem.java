package com.valor.commerce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "InvoiceLineItem")
@Table(name = "invoice_line_items")
@Getter
@Setter
class InvoiceLineItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "pricing_code", nullable = false, length = 80)
    private String pricingCode;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitAmount;

    @Column(name = "line_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineAmount;
}
