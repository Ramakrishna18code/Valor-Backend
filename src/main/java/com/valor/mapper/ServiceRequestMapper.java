package com.valor.mapper;

import com.valor.entity.Customer;
import com.valor.entity.Lift;
import com.valor.entity.Technician;
import com.valor.entity.ServiceRequest;
import com.valor.request.ServiceRequestRequest;
import com.valor.response.ServiceRequestResponse;

public final class ServiceRequestMapper {

    private ServiceRequestMapper() {
    }

    public static ServiceRequest toEntity(ServiceRequestRequest request) {
        ServiceRequest serviceRequest = new ServiceRequest();
        serviceRequest.setTitle(request.title());
        serviceRequest.setDescription(request.description());
        serviceRequest.setIssueCategory(request.issueCategory());
        serviceRequest.setPriority(request.priority());
        serviceRequest.setStatus(request.status());
        serviceRequest.setServiceType(request.serviceType());
        serviceRequest.setCustomerRemarks(request.customerRemarks());
        serviceRequest.setTechnicianRemarks(request.technicianRemarks());
        serviceRequest.setServiceRequestedAt(request.serviceRequestedAt());
        serviceRequest.setPreferredVisitDate(request.preferredVisitDate());
        serviceRequest.setPreferredTimeSlot(request.preferredTimeSlot());
        serviceRequest.setInternalAdminNotes(request.internalAdminNotes());
        Customer customer = new Customer();
        customer.setId(request.customerId());
        serviceRequest.setCustomer(customer);
        Lift lift = new Lift();
        lift.setId(request.liftId());
        serviceRequest.setLift(lift);
        if (request.assignedTechnicianId() != null) {
            Technician technician = new Technician();
            technician.setId(request.assignedTechnicianId());
            serviceRequest.setAssignedTechnician(technician);
        }
        return serviceRequest;
    }

    public static ServiceRequestResponse toResponse(ServiceRequest serviceRequest) {
        return new ServiceRequestResponse(
                serviceRequest.getId(),
                serviceRequest.getServiceId(),
                serviceRequest.getCustomer() == null ? null : serviceRequest.getCustomer().getId(),
                serviceRequest.getLift() == null ? null : serviceRequest.getLift().getId(),
                serviceRequest.getAssignedTechnician() == null ? null : serviceRequest.getAssignedTechnician().getId(),
                serviceRequest.getTitle(),
                serviceRequest.getDescription(),
                serviceRequest.getIssueCategory(),
                serviceRequest.getPriority(),
                serviceRequest.getStatus(),
                serviceRequest.getServiceType(),
                serviceRequest.getCustomerRemarks(),
                serviceRequest.getTechnicianRemarks(),
                serviceRequest.getServiceRequestedAt(),
                serviceRequest.getPreferredVisitDate(),
                serviceRequest.getPreferredTimeSlot(),
                serviceRequest.getInternalAdminNotes(),
                serviceRequest.getAssignedAt(),
                serviceRequest.getStartedAt(),
                serviceRequest.getCompletedAt(),
                serviceRequest.getCustomerSignaturePath(),
                serviceRequest.getServiceReportPath()
        );
    }
}