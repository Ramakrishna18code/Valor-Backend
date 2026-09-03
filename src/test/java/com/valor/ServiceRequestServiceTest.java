package com.valor;

import com.valor.entity.ServiceRequest;
import com.valor.repository.ServiceRequestRepository;
import com.valor.service.impl.ServiceRequestServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class ServiceRequestServiceTest {

    @Autowired
    private ServiceRequestRepository repository;

    @Test
    void createAndFetchServiceRequest() {
        ServiceRequestServiceImpl service = new ServiceRequestServiceImpl(repository);
        ServiceRequest sr = ServiceRequest.builder()
                .serviceId("SR-001")
                .title("Lift stuck")
                .description("Lift not responding")
                .build();

        ServiceRequest saved = service.createServiceRequest(sr);
        assertThat(saved.getId()).isNotNull();

        ServiceRequest fetched = service.getServiceRequest(saved.getId());
        assertThat(fetched.getServiceId()).isEqualTo("SR-001");
    }
}
