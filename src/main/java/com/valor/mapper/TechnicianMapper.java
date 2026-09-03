package com.valor.mapper;

import com.valor.entity.Technician;
import com.valor.enums.RoleName;
import com.valor.request.TechnicianRequest;
import com.valor.response.TechnicianResponse;

public final class TechnicianMapper {

    private TechnicianMapper() {
    }

    public static Technician toEntity(TechnicianRequest request) {
        Technician technician = new Technician();
        technician.setName(request.name());
        technician.setEmail(request.email());
        technician.setPassword(request.password());
        technician.setPhone(request.phone());
        technician.setEmployeeId(request.employeeId());
        technician.setAssignedArea(request.assignedArea());
        technician.setSpecialization(request.specialization());
        technician.setCurrentWorkload(request.currentWorkload() == null ? 0 : request.currentWorkload());
        technician.setPendingJobs(request.pendingJobs() == null ? 0 : request.pendingJobs());
        technician.setRating(request.rating());
        technician.setRole(request.role() == null ? RoleName.TECHNICIAN : request.role());
        technician.setAvailabilityStatus(request.availabilityStatus() == null ? null : request.availabilityStatus());
        return technician;
    }

    public static TechnicianResponse toResponse(Technician technician) {
        return new TechnicianResponse(
            technician.getId(),
            technician.getName(),
            technician.getEmail(),
            technician.getPhone(),
            technician.getEmployeeId(),
            technician.getAssignedArea(),
            technician.getSpecialization(),
            technician.getCurrentWorkload(),
            technician.getPendingJobs(),
            technician.getRating(),
            technician.getRole() == null ? null : technician.getRole().name(),
            technician.getAvailabilityStatus() == null ? null : technician.getAvailabilityStatus().name()
        );
    }
}