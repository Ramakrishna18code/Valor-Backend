package com.valor.service.impl;

import com.valor.enums.JobStatus;

import com.valor.repository.*;
import com.valor.response.ReportSummaryResponse;
import com.valor.service.ReportService;
import org.springframework.stereotype.Service;

@Service
public class ReportServiceImpl implements ReportService {

    private final CustomerRepository customerRepository;
    private final LiftRepository liftRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final TechnicianRepository technicianRepository;
    private final AmcRepository amcRepository;
    private final InventoryRepository inventoryRepository;
    private final NotificationRepository notificationRepository;
    private final PaymentRepository paymentRepository;
    private final AttendanceRepository attendanceRepository;

    public ReportServiceImpl(CustomerRepository customerRepository,
                             LiftRepository liftRepository,
                             ServiceRequestRepository serviceRequestRepository,
                             TechnicianRepository technicianRepository,
                             AmcRepository amcRepository,
                             InventoryRepository inventoryRepository,
                             NotificationRepository notificationRepository,
                             PaymentRepository paymentRepository,
                             AttendanceRepository attendanceRepository) {
        this.customerRepository = customerRepository;
        this.liftRepository = liftRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.technicianRepository = technicianRepository;
        this.amcRepository = amcRepository;
        this.inventoryRepository = inventoryRepository;
        this.notificationRepository = notificationRepository;
        this.paymentRepository = paymentRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @Override
    public ReportSummaryResponse getSummary() {
        long pendingJobs = serviceRequestRepository.findAll().stream().filter(request -> request.getStatus() == JobStatus.PENDING || request.getStatus() == JobStatus.ASSIGNED).count();
        long completedJobs = serviceRequestRepository.findAll().stream().filter(request -> request.getStatus() == JobStatus.COMPLETED).count();
        long emergencyJobs = serviceRequestRepository.findAll().stream().filter(request -> request.getPriority() != null && "EMERGENCY".equals(request.getPriority().name())).count();
        long lowStockItems = inventoryRepository.findByStockQuantityLessThanEqual(0).size();
        long totalNotifications = notificationRepository.count();
        long totalPayments = paymentRepository.count();
        long totalAttendanceRecords = attendanceRepository.count();

        return new ReportSummaryResponse(
                customerRepository.count(),
                liftRepository.count(),
                serviceRequestRepository.count(),
                pendingJobs,
                completedJobs,
                emergencyJobs,
                technicianRepository.count(),
                amcRepository.count(),
                inventoryRepository.count(),
                lowStockItems,
                totalNotifications,
                totalPayments,
                totalAttendanceRecords
        );
    }
}