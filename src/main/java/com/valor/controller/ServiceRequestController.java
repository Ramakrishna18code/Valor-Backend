package com.valor.controller;

import com.valor.entity.ServiceRequest;
import com.valor.mapper.ServiceRequestMapper;
import com.valor.request.ServiceRequestRequest;
import com.valor.response.ApiResponse;
import com.valor.response.ServiceRequestResponse;
import com.valor.enums.JobStatus;
import com.valor.service.ServiceRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/service-requests")
@Tag(name = "Service Requests", description = "Service request lifecycle management")
public class ServiceRequestController {

    private final ServiceRequestService serviceRequestService;

    public ServiceRequestController(ServiceRequestService serviceRequestService) {
        this.serviceRequestService = serviceRequestService;
    }

    @Operation(summary = "Create service request")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> createServiceRequest(@jakarta.validation.Valid @RequestBody ServiceRequestRequest request) {
        ServiceRequest serviceRequest = ServiceRequestMapper.toEntity(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Service request created", ServiceRequestMapper.toResponse(serviceRequestService.createServiceRequest(serviceRequest)), HttpStatus.CREATED.value()));
    }

    @Operation(summary = "Get service request")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> getServiceRequest(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Service request fetched", ServiceRequestMapper.toResponse(serviceRequestService.getServiceRequest(id)), HttpStatus.OK.value()));
    }

    @Operation(summary = "List service requests")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ServiceRequestResponse>>> getAllServiceRequests() {
        return ResponseEntity.ok(ApiResponse.success("Service requests fetched", serviceRequestService.getAllServiceRequests().stream().map(ServiceRequestMapper::toResponse).collect(Collectors.toList()), HttpStatus.OK.value()));
    }

    @Operation(summary = "Search service requests")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<ServiceRequestResponse>>> searchServiceRequests(
            @RequestParam String term,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "serviceRequestedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ServiceRequestResponse> response = serviceRequestService.searchServiceRequests(term, pageable)
                .map(ServiceRequestMapper::toResponse);
        return ResponseEntity.ok(ApiResponse.success("Service requests search completed", response, HttpStatus.OK.value()));
    }

    @Operation(summary = "Update service request")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> updateServiceRequest(@PathVariable Long id, @RequestBody ServiceRequestRequest request) {
        ServiceRequest serviceRequest = ServiceRequestMapper.toEntity(request);
        return ResponseEntity.ok(ApiResponse.success("Service request updated", ServiceRequestMapper.toResponse(serviceRequestService.updateServiceRequest(id, serviceRequest)), HttpStatus.OK.value()));
    }

    @Operation(summary = "Assign a technician")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> assignServiceRequest(@PathVariable Long id,
                                                                                     @RequestParam Long technicianId) {
        ServiceRequest assigned = serviceRequestService.assignServiceRequest(id, technicianId);
        return ResponseEntity.ok(ApiResponse.success("Service request assigned", ServiceRequestMapper.toResponse(assigned), HttpStatus.OK.value()));
    }

    @Operation(summary = "Start a service request")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'TECHNICIAN')")
    @PutMapping("/{id}/start")
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> startServiceRequest(@PathVariable Long id) {
        return statusResponse(id, JobStatus.IN_PROGRESS, "Service request started");
    }

    @Operation(summary = "Complete a service request")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'TECHNICIAN')")
    @PutMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> completeServiceRequest(@PathVariable Long id) {
        return statusResponse(id, JobStatus.COMPLETED, "Service request completed");
    }

    private ResponseEntity<ApiResponse<ServiceRequestResponse>> statusResponse(Long id, JobStatus status, String message) {
        return ResponseEntity.ok(ApiResponse.success(message,
                ServiceRequestMapper.toResponse(serviceRequestService.updateStatus(id, status)), HttpStatus.OK.value()));
    }

    @Operation(summary = "Delete service request")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteServiceRequest(@PathVariable Long id) {
        serviceRequestService.deleteServiceRequest(id);
        return ResponseEntity.ok(ApiResponse.success("Service request deleted", null, HttpStatus.OK.value()));
    }
}
