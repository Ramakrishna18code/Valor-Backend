package com.valor.service.impl;

import com.valor.enums.JobStatus;
import com.valor.enums.PriorityLevel;
import com.valor.repository.*;
import com.valor.response.ReportSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {
    @Mock CustomerRepository customers;
    @Mock LiftRepository lifts;
    @Mock ServiceRequestRepository requests;
    @Mock TechnicianRepository technicians;
    @Mock AmcRepository amcs;
    @Mock InventoryRepository inventory;
    @Mock NotificationRepository notifications;
    @Mock PaymentRepository payments;
    @Mock AttendanceRepository attendance;

    @Test
    void summaryUsesDatabaseCountsInsteadOfLoadingAllRows() {
        when(requests.countByStatus(JobStatus.PENDING)).thenReturn(2L);
        when(requests.countByStatus(JobStatus.ASSIGNED)).thenReturn(3L);
        when(requests.countByStatus(JobStatus.COMPLETED)).thenReturn(4L);
        when(requests.countByPriority(PriorityLevel.EMERGENCY)).thenReturn(5L);
        when(inventory.countByStockQuantityLessThanEqual(0)).thenReturn(6L);

        ReportSummaryResponse result = new ReportServiceImpl(customers, lifts, requests, technicians, amcs,
                inventory, notifications, payments, attendance).getSummary();

        assertEquals(5L, result.pendingJobs());
        assertEquals(4L, result.completedJobs());
        assertEquals(5L, result.emergencyJobs());
        assertEquals(6L, result.lowStockItems());
        verify(requests, never()).findAll();
        verify(inventory, never()).findByStockQuantityLessThanEqual(0);
    }
}
