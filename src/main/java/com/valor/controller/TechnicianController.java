package com.valor.controller;

import com.valor.entity.Technician;
import com.valor.mapper.TechnicianMapper;
import com.valor.request.TechnicianRequest;
import com.valor.response.ApiResponse;
import com.valor.response.TechnicianResponse;
import com.valor.repository.TechnicianRepository;
import com.valor.repository.ServiceRequestRepository;
import com.valor.mapper.ServiceRequestMapper;
import com.valor.response.ServiceRequestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/technicians")
@Tag(name = "Technicians", description = "Technician management and service operations")
public class TechnicianController {

    private final TechnicianRepository technicianRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final PasswordEncoder passwordEncoder;

    public TechnicianController(TechnicianRepository technicianRepository,
                                ServiceRequestRepository serviceRequestRepository,
                                PasswordEncoder passwordEncoder) {
        this.technicianRepository = technicianRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Operation(summary = "Create technician")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<TechnicianResponse>> createTechnician(@Valid @RequestBody TechnicianRequest request) {
        Technician technician = TechnicianMapper.toEntity(request);
        technician.setPassword(passwordEncoder.encode(request.password()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Technician created", TechnicianMapper.toResponse(technicianRepository.save(technician)), HttpStatus.CREATED.value()));
    }

    @Operation(summary = "List technicians")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TechnicianResponse>>> getAllTechnicians() {
        return ResponseEntity.ok(ApiResponse.success("Technicians fetched", technicianRepository.findAll().stream().map(TechnicianMapper::toResponse).collect(Collectors.toList()), HttpStatus.OK.value()));
    }

    @Operation(summary = "Search technicians")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<TechnicianResponse>>> searchTechnicians(
            @RequestParam String term,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<TechnicianResponse> response = technicianRepository.search(term, pageable).map(TechnicianMapper::toResponse);
        return ResponseEntity.ok(ApiResponse.success("Technicians search completed", response, HttpStatus.OK.value()));
    }

    @Operation(summary = "List assignments for a technician")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'TECHNICIAN')")
    @GetMapping("/{id}/assignments")
    public ResponseEntity<ApiResponse<List<ServiceRequestResponse>>> getAssignments(@PathVariable Long id) {
        if (!technicianRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Technician not found", HttpStatus.NOT_FOUND.value()));
        }
        List<ServiceRequestResponse> response = serviceRequestRepository
                .findByAssignedTechnicianIdOrderByServiceRequestedAtDesc(id)
                .stream()
                .map(ServiceRequestMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Assignments fetched", response, HttpStatus.OK.value()));
    }
}
