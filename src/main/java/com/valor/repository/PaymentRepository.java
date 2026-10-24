package com.valor.repository;

import com.valor.entity.Payment;
import com.valor.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);
    Page<Payment> findByCustomerId(Long customerId, Pageable pageable);
    Page<Payment> findByCustomerIdAndStatus(Long customerId, PaymentStatus status, Pageable pageable);
}
