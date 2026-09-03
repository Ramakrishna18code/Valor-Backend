package com.valor.controller;

import com.valor.entity.Amc;
import com.valor.entity.Customer;
import com.valor.entity.Payment;
import com.valor.enums.PaymentStatus;
import com.valor.exception.ResourceNotFoundException;
import com.valor.repository.AmcRepository;
import com.valor.repository.CustomerRepository;
import com.valor.repository.PaymentRepository;
import com.valor.request.PaymentRequest;
import com.valor.response.ApiResponse;
import com.valor.response.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Payments", description = "Payment recording and receipt APIs")
public class PaymentController {
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final AmcRepository amcRepository;

    public PaymentController(PaymentRepository paymentRepository,
                             CustomerRepository customerRepository,
                             AmcRepository amcRepository) {
        this.paymentRepository = paymentRepository;
        this.customerRepository = customerRepository;
        this.amcRepository = amcRepository;
    }

    @Operation(summary = "Record payment")
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> create(@Valid @RequestBody PaymentRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        Payment payment = new Payment();
        payment.setCustomer(customer);
        payment.setAmc(resolveAmc(request.amcId()));
        payment.setAmount(request.amount());
        payment.setGstAmount(request.gstAmount());
        payment.setTotalAmount(request.totalAmount() == null ? request.amount() : request.totalAmount());
        payment.setPaymentMode(request.paymentMode());
        payment.setStatus(request.status() == null ? PaymentStatus.PENDING : request.status());
        payment.setPaymentDateTime(request.paymentDateTime() == null ? LocalDateTime.now() : request.paymentDateTime());
        payment.setInvoiceNumber(request.invoiceNumber() == null ? invoiceNumber() : request.invoiceNumber());
        payment.setReceiptNumber(request.receiptNumber());
        Payment saved = paymentRepository.save(payment);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment recorded successfully", toResponse(saved), HttpStatus.CREATED.value()));
    }

    @Operation(summary = "List payments")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> list(@RequestParam(required = false) Long customerId,
                                                                    @RequestParam(required = false) PaymentStatus status) {
        List<Payment> payments = status == null ? paymentRepository.findAll() : paymentRepository.findByStatus(status);
        if (customerId != null) payments = payments.stream()
                .filter(payment -> payment.getCustomer() != null && customerId.equals(payment.getCustomer().getId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Payments fetched", payments.stream().map(PaymentController::toResponse).collect(Collectors.toList()), HttpStatus.OK.value()));
    }

    @Operation(summary = "Get payment")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> get(@PathVariable Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        return ResponseEntity.ok(ApiResponse.success("Payment fetched", toResponse(payment), HttpStatus.OK.value()));
    }

    private Amc resolveAmc(Long id) {
        if (id == null) return null;
        return amcRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("AMC not found"));
    }

    private static String invoiceNumber() {
        return "VAL-INV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(),
                payment.getCustomer() == null ? null : payment.getCustomer().getId(),
                payment.getAmc() == null ? null : payment.getAmc().getId(),
                payment.getInvoiceNumber(), payment.getAmount(), payment.getGstAmount(), payment.getTotalAmount(),
                payment.getPaymentMode(), payment.getStatus(), payment.getPaymentDateTime(), payment.getReceiptNumber());
    }
}
