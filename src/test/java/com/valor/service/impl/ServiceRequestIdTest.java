package com.valor.service.impl;

import com.valor.entity.Customer;
import com.valor.entity.Lift;
import com.valor.entity.ServiceRequest;
import com.valor.repository.ServiceRequestRepository;
import com.valor.workflow.ServiceRequestIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceRequestIdTest {
    @Test
    void createsLiftBasedReferenceAfterRequestReceivesItsNumber() {
        ServiceRequestRepository repository = mock(ServiceRequestRepository.class);
        when(repository.saveAndFlush(any(ServiceRequest.class))).thenAnswer(invocation -> {
            ServiceRequest saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(42L);
            return saved;
        });
        Customer customer = new Customer();
        customer.setId(7L);
        Lift lift = new Lift();
        lift.setId(15L);
        ServiceRequest request = new ServiceRequest();
        request.setCustomer(customer);
        request.setLift(lift);

        ServiceRequest saved = new ServiceRequestServiceImpl(repository).createServiceRequest(request);

        assertEquals("SR-LFT-15-42", saved.getServiceId());
        verify(repository, times(2)).saveAndFlush(any(ServiceRequest.class));
    }

    @Test
    void installationWithoutLiftUsesRequestNumber() {
        assertEquals("SR-NEW-42", ServiceRequestIds.forRequest(null, 42L));
    }
}
