package com.valor.controller;

import com.valor.entity.Admin;
import com.valor.mapper.AdminMapper;
import com.valor.request.AdminRequest;
import com.valor.response.ApiResponse;
import com.valor.response.AdminResponse;
import com.valor.response.AdminDashboardResponse;
import com.valor.response.ServiceRequestResponse;
import com.valor.repository.AdminRepository;
import com.valor.repository.AmcRepository;
import com.valor.repository.CustomerRepository;
import com.valor.repository.LiftRepository;
import com.valor.repository.ServiceRequestRepository;
import com.valor.repository.TechnicianRepository;
import com.valor.enums.JobStatus;
import com.valor.enums.PriorityLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin", description = "Admin dashboard and management APIs")
public class AdminController {

    private final AdminRepository adminRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final CustomerRepository customerRepository;
    private final LiftRepository liftRepository;
    private final TechnicianRepository technicianRepository;
    private final AmcRepository amcRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminController(AdminRepository adminRepository,
                           ServiceRequestRepository serviceRequestRepository,
                           CustomerRepository customerRepository,
                           LiftRepository liftRepository,
                           TechnicianRepository technicianRepository,
                           AmcRepository amcRepository,
                           PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.customerRepository = customerRepository;
        this.liftRepository = liftRepository;
        this.technicianRepository = technicianRepository;
        this.amcRepository = amcRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Operation(summary = "Create admin")
    @PostMapping
    public ResponseEntity<ApiResponse<AdminResponse>> createAdmin(@Valid @RequestBody AdminRequest request) {
        Admin admin = AdminMapper.toEntity(request);
        admin.setPassword(passwordEncoder.encode(request.password()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Admin created", AdminMapper.toResponse(adminRepository.save(admin)), HttpStatus.CREATED.value()));
    }

        @Operation(summary = "Get the authenticated admin")
        @GetMapping("/me")
        public ResponseEntity<ApiResponse<AdminResponse>> getCurrentAdmin(Authentication authentication) {
        Admin admin = adminRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new IllegalArgumentException("Authenticated admin no longer exists"));
        return ResponseEntity.ok(ApiResponse.success("Admin profile fetched successfully",
            AdminMapper.toResponse(admin), HttpStatus.OK.value()));
        }

    @Operation(summary = "Get admin by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminResponse>> getAdminById(@PathVariable Long id) {
        return adminRepository.findById(id)
                .map(a -> ResponseEntity.ok(ApiResponse.success("Admin fetched", AdminMapper.toResponse(a), HttpStatus.OK.value())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Admin not found", HttpStatus.NOT_FOUND.value())));
    }

    @Operation(summary = "Update admin")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminResponse>> updateAdmin(@PathVariable Long id, @Valid @RequestBody AdminRequest request) {
        return adminRepository.findById(id).map(existing -> {
            existing.setName(request.name());
            existing.setEmail(request.email());
            existing.setPhone(request.phone());
            existing.setPassword(passwordEncoder.encode(request.password()));
            existing.setRole(request.role() == null ? existing.getRole() : request.role());
            existing.setActive(request.active() == null ? existing.getActive() : request.active());
            existing.setEmployeeId(request.employeeId());
            existing.setDesignation(request.designation());
            Admin saved = adminRepository.save(existing);
            return ResponseEntity.ok(ApiResponse.success("Admin updated", AdminMapper.toResponse(saved), HttpStatus.OK.value()));
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Admin not found", HttpStatus.NOT_FOUND.value())));
    }

    @Operation(summary = "Delete admin")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteAdmin(@PathVariable Long id) {
        return adminRepository.findById(id).map(existing -> {
            adminRepository.delete(existing);
            return ResponseEntity.ok(ApiResponse.success("Admin deleted", null, HttpStatus.OK.value()));
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Admin not found", HttpStatus.NOT_FOUND.value())));
    }

    @Operation(summary = "List admins")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminResponse>>> getAllAdmins() {
        return ResponseEntity.ok(ApiResponse.success("Admins fetched", adminRepository.findAll().stream().map(AdminMapper::toResponse).collect(Collectors.toList()), HttpStatus.OK.value()));
    }

        @Operation(summary = "Get admin service dashboard")
        @GetMapping("/dashboard/service-jobs")
        public ResponseEntity<ApiResponse<AdminDashboardResponse>> getServiceDashboard() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        List<ServiceRequestResponse> allRequests = serviceRequestRepository.findAll().stream()
            .map(com.valor.mapper.ServiceRequestMapper::toResponse)
            .toList();

        List<ServiceRequestResponse> todaysJobs = allRequests.stream()
            .filter(job -> job.serviceRequestedAt() != null && job.serviceRequestedAt().toLocalDate().isEqual(today))
            .collect(Collectors.toList());

        List<ServiceRequestResponse> tomorrowJobs = allRequests.stream()
            .filter(job -> {
                Object pv = job.preferredVisitDate();
                if (pv == null) return false;
                if (pv instanceof java.time.LocalDate) {
                    return ((java.time.LocalDate) pv).isEqual(tomorrow);
                }
                if (pv instanceof java.time.LocalDateTime) {
                    return ((java.time.LocalDateTime) pv).toLocalDate().isEqual(tomorrow);
                }
                return false;
            })
            .collect(Collectors.toList());

        List<ServiceRequestResponse> pendingJobs = allRequests.stream()
            .filter(job -> job.status() != null && (job.status() == JobStatus.PENDING || job.status() == JobStatus.ASSIGNED))
            .collect(Collectors.toList());

        List<ServiceRequestResponse> inProgressJobs = allRequests.stream()
            .filter(job -> job.status() != null && (job.status() == JobStatus.IN_PROGRESS || job.status() == JobStatus.ON_THE_WAY))
            .collect(Collectors.toList());

        List<ServiceRequestResponse> completedJobs = allRequests.stream()
            .filter(job -> job.status() != null && job.status() == JobStatus.COMPLETED)
            .collect(Collectors.toList());

        List<ServiceRequestResponse> emergencyJobs = allRequests.stream()
            .filter(job -> job.priority() != null && job.priority() == PriorityLevel.EMERGENCY)
            .collect(Collectors.toList());

        AdminDashboardResponse response = new AdminDashboardResponse(
            todaysJobs.size(),
            tomorrowJobs.size(),
            completedJobs.size(),
            pendingJobs.size(),
            emergencyJobs.size(),
            customerRepository.count(),
            technicianRepository.count(),
            liftRepository.count(),
            amcRepository.count(),
            todaysJobs,
            tomorrowJobs,
            pendingJobs,
            inProgressJobs,
            completedJobs,
            emergencyJobs
        );

        return ResponseEntity.ok(ApiResponse.success("Service dashboard fetched", response, HttpStatus.OK.value()));
        }
}
