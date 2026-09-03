package com.valor.controller;

import com.valor.entity.ServiceRequest;
import com.valor.enums.JobStatus;
import com.valor.enums.PriorityLevel;
import com.valor.repository.AmcRepository;
import com.valor.repository.AdminRepository;
import com.valor.repository.CustomerRepository;
import com.valor.repository.LiftRepository;
import com.valor.repository.ServiceRequestRepository;
import com.valor.repository.TechnicianRepository;
import com.valor.response.AdminDashboardResponse;
import com.valor.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminControllerTest {

    @Mock
    AdminRepository adminRepository;
    @Mock
    ServiceRequestRepository serviceRequestRepository;
    @Mock
    CustomerRepository customerRepository;
    @Mock
    LiftRepository liftRepository;
    @Mock
    TechnicianRepository technicianRepository;
    @Mock
    AmcRepository amcRepository;

    @InjectMocks
    AdminController controller;

    @Test
    void dashboardFiltersCountsCorrectly() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        ServiceRequest srToday = new ServiceRequest();
        srToday.setServiceRequestedAt(now);

        ServiceRequest srTomorrow = new ServiceRequest();
        srTomorrow.setPreferredVisitDate(today.plusDays(1));

        ServiceRequest srPending = new ServiceRequest();
        srPending.setStatus(JobStatus.PENDING);

        ServiceRequest srEmergency = new ServiceRequest();
        srEmergency.setPriority(PriorityLevel.EMERGENCY);

        List<ServiceRequest> entities = List.of(srToday, srTomorrow, srPending, srEmergency);

        when(serviceRequestRepository.findAll()).thenReturn(entities);
        when(customerRepository.count()).thenReturn(5L);
        when(technicianRepository.count()).thenReturn(2L);
        when(liftRepository.count()).thenReturn(3L);
        when(amcRepository.count()).thenReturn(1L);

        var responseEntity = controller.getServiceDashboard();
        ApiResponse<AdminDashboardResponse> api = responseEntity.getBody();
        assertThat(api).isNotNull();
        AdminDashboardResponse data = api.getData();

        // Note: mapping converts entities to ServiceRequestResponse; counts should reflect the mocked entities
        assertThat(data.totalCustomers()).isEqualTo(5L);
        assertThat(data.totalTechnicians()).isEqualTo(2L);
        assertThat(data.totalLifts()).isEqualTo(3L);
        assertThat(data.totalAmcs()).isEqualTo(1L);

        assertThat(data.totalJobsToday()).isEqualTo(1L);
        assertThat(data.jobsScheduledTomorrow()).isEqualTo(1L);
        assertThat(data.pendingJobs()).isEqualTo(1L);
        assertThat(data.emergencyJobs()).isEqualTo(1L);
    }
}
