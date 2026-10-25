package com.valor.controller;

import com.valor.entity.Payment;
import com.valor.enums.PaymentStatus;
import com.valor.repository.AmcRepository;
import com.valor.repository.CustomerRepository;
import com.valor.repository.PaymentRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentControllerPaginationTest {
    private PaymentRepository payments;
    private PaymentController controller;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRepository.class);
        controller = new PaymentController(payments, mock(CustomerRepository.class), mock(AmcRepository.class));
    }

    @Test
    void defaultsToFirstPageOfTwentyAndUsesDatabasePaging() {
        when(payments.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list(null, null, 0, 20);

        verify(payments).findAll(eq(Pageable.ofSize(20)));
    }

    @Test
    void filtersStatusAndCustomerAtRepositoryLayer() {
        when(payments.findByCustomerIdAndStatus(eq(7L), eq(PaymentStatus.PAID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.list(7L, PaymentStatus.PAID, 2, 100);

        verify(payments).findByCustomerIdAndStatus(eq(7L), eq(PaymentStatus.PAID), eq(Pageable.ofSize(100).withPage(2)));
    }

    @Test
    void rejectsInvalidPaging() {
        assertThrows(IllegalArgumentException.class, () -> controller.list(null, null, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> controller.list(null, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> controller.list(null, null, 0, 101));
    }
}
