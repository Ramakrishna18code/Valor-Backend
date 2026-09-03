package com.valor.service;

import com.valor.entity.ServiceRequest;
import com.valor.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ServiceRequestService {
    ServiceRequest createServiceRequest(ServiceRequest serviceRequest);
    ServiceRequest updateServiceRequest(Long id, ServiceRequest serviceRequest);
    void deleteServiceRequest(Long id);
    ServiceRequest getServiceRequest(Long id);
    List<ServiceRequest> getAllServiceRequests();
    List<ServiceRequest> getServiceRequestsByCustomerId(Long customerId);
    ServiceRequest assignServiceRequest(Long id, Long technicianId);
    ServiceRequest updateStatus(Long id, JobStatus status);
    Page<ServiceRequest> searchServiceRequests(String term, Pageable pageable);
}
