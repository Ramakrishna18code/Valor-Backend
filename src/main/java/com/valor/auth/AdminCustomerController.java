package com.valor.auth;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.valor.response.ApiResponse;
import com.valor.workflow.WorkflowDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/customers")
class AdminCustomerController {
    private final AdminCustomerService customers;
    AdminCustomerController(AdminCustomerService customers) { this.customers = customers; }

    record CustomerCreateRequest(@Size(max=254) String email, @Size(max=20) String phone,
            @Schema(accessMode=Schema.AccessMode.WRITE_ONLY, format="password") @NotBlank @Size(max=72) String password,
            @NotBlank @Size(max=160) String fullName, @Size(max=20) String alternatePhone,
            @Size(max=200) String companyName, @Size(max=500) String address) {
        @JsonAnySetter public void unknown(String key, JsonNode value) { throw new IllegalArgumentException("Unsupported field"); }
    }
    record CustomerUpdateRequest(@NotBlank @Size(max=160) String fullName, @Size(max=20) String alternatePhone,
            @Size(max=200) String companyName, @Size(max=500) String address) {
        @JsonAnySetter public void unknown(String key, JsonNode value) { throw new IllegalArgumentException("Unsupported field"); }
    }
    record StateRequest(@Size(max=2000) String reason) {
        @JsonAnySetter public void unknown(String key, JsonNode value) { throw new IllegalArgumentException("Unsupported field"); }
    }
    record BuildingSummary(Long id, String buildingName, String buildingType, String city, String status, boolean isActive) {}
    record LiftSummary(Long id, Long buildingId, String name, String liftNumber, String currentStatus, boolean isActive) {}
    record AdminCustomerDetail(Long userId, Long customerProfileId, String email, String phone, String fullName,
            String alternatePhone, String companyName, String address, boolean active, String status,
            LocalDateTime createdAt, LocalDateTime updatedAt, long buildingCount, long liftCount,
            long serviceRequestCount, List<BuildingSummary> buildings, List<LiftSummary> lifts,
            List<WorkflowDtos.RequestView> serviceRequests) {}

    @Operation(operationId="createAdminCustomer")
    @PostMapping ApiResponse<AdminCustomerDetail> create(@Valid @RequestBody CustomerCreateRequest input) {
        return ApiResponse.success("Customer created", customers.create(input), 200);
    }
    @Operation(operationId="getAdminCustomer")
    @GetMapping("/{customerProfileId}") ApiResponse<AdminCustomerDetail> get(@PathVariable Long customerProfileId) {
        return ApiResponse.success("Customer", customers.detail(customerProfileId), 200);
    }
    @Operation(operationId="updateAdminCustomer")
    @PutMapping("/{customerProfileId}") ApiResponse<AdminCustomerDetail> update(@PathVariable Long customerProfileId, @Valid @RequestBody CustomerUpdateRequest input) {
        return ApiResponse.success("Customer updated", customers.update(customerProfileId, input), 200);
    }
    @Operation(operationId="deactivateAdminCustomer")
    @PostMapping("/{customerProfileId}/deactivate") ApiResponse<AdminCustomerDetail> deactivate(@PathVariable Long customerProfileId, @RequestBody(required=false) StateRequest input) {
        return ApiResponse.success("Customer deactivated", customers.setActive(customerProfileId, false), 200);
    }
    @Operation(operationId="reactivateAdminCustomer")
    @PostMapping("/{customerProfileId}/reactivate") ApiResponse<AdminCustomerDetail> reactivate(@PathVariable Long customerProfileId, @RequestBody(required=false) StateRequest input) {
        return ApiResponse.success("Customer reactivated", customers.setActive(customerProfileId, true), 200);
    }
}

@Service
@Transactional
class AdminCustomerService {
    private final UserRepo users; private final CustomerRepo profiles; private final AuthService auth;
    private final AssetIdentityAccess identities; private final PasswordEncoder encoder; private final EntityManager em;
    AdminCustomerService(UserRepo users, CustomerRepo profiles, AuthService auth, AssetIdentityAccess identities,
            PasswordEncoder encoder, EntityManager em) {
        this.users=users; this.profiles=profiles; this.auth=auth; this.identities=identities; this.encoder=encoder; this.em=em;
    }
    AdminCustomerController.AdminCustomerDetail create(AdminCustomerController.CustomerCreateRequest input) {
        identities.requireAdmin();
        String email=auth.normEmail(input.email());
        String phone=input.phone()==null || input.phone().isBlank() ? null : auth.normPhone(input.phone());
        if(email!=null && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Invalid email");
        if(email==null && phone==null) throw new IllegalArgumentException("Email or phone required");
        if(input.password()==null || input.password().isBlank() || input.password().getBytes(StandardCharsets.UTF_8).length>72) throw new IllegalArgumentException("Invalid password");
        if((email!=null && users.findByEmail(email).isPresent()) || (phone!=null && users.findByPhone(phone).isPresent())) throw new IllegalArgumentException("Identity already exists");
        User user=new User(); user.setEmail(email); user.setPhone(phone); user.setPasswordHash(encoder.encode(input.password())); user.setRole(Role.CUSTOMER); users.saveAndFlush(user);
        CustomerProfile profile=new CustomerProfile(); profile.user=user; profile.fullName=clean(input.fullName()); profile.alternatePhone=clean(input.alternatePhone());
        profile.companyName=clean(input.companyName()); profile.address=clean(input.address()); profile.status="ACTIVE"; profile.active=true; profiles.saveAndFlush(profile);
        return view(profile);
    }
    AdminCustomerController.AdminCustomerDetail update(Long id, AdminCustomerController.CustomerUpdateRequest input) {
        identities.requireAdmin();
        CustomerProfile profile=lock(id);
        profile.fullName=clean(input.fullName()); profile.alternatePhone=clean(input.alternatePhone());
        profile.companyName=clean(input.companyName()); profile.address=clean(input.address());
        em.flush();
        return view(profile);
    }
    AdminCustomerController.AdminCustomerDetail setActive(Long id, boolean active) {
        identities.requireAdmin();
        CustomerProfile profile=lock(id);
        profile.active=active; profile.status=active ? "ACTIVE" : "INACTIVE"; profile.user.setActive(active);
        em.flush();
        return view(profile);
    }
    @Transactional(readOnly=true)
    AdminCustomerController.AdminCustomerDetail detail(Long id) {
        identities.requireAdmin();
        CustomerProfile profile=profiles.findById(id).orElseThrow(() -> new CustomerNotFoundException());
        if(profile.getUser().getRole()!=Role.CUSTOMER) throw new CustomerNotFoundException();
        return view(profile);
    }
    private CustomerProfile lock(Long id) {
        CustomerProfile profile=em.find(CustomerProfile.class, id, LockModeType.PESSIMISTIC_WRITE);
        if(profile==null || profile.getUser().getRole()!=Role.CUSTOMER) throw new CustomerNotFoundException();
        em.lock(profile.getUser(), LockModeType.PESSIMISTIC_WRITE);
        return profile;
    }
    private AdminCustomerController.AdminCustomerDetail view(CustomerProfile profile) {
        Long id=profile.id;
        var buildings=em.createQuery("select b from Building b where b.customer.id=:id order by b.id", com.valor.assets.Building.class).setParameter("id", id).setMaxResults(25).getResultList().stream()
            .map(b -> new AdminCustomerController.BuildingSummary(b.getId(), b.getBuildingName(), b.getBuildingType(), b.getCity(), b.getStatus(), b.isActive())).toList();
        var lifts=em.createQuery("select l from Lift l where l.building.customer.id=:id order by l.id", com.valor.assets.Lift.class).setParameter("id", id).setMaxResults(25).getResultList().stream()
            .map(l -> new AdminCustomerController.LiftSummary(l.getId(), l.getBuilding().getId(), l.getName(), l.getLiftNumber(), String.valueOf(l.getCurrentStatus()), l.isActive())).toList();
        var requests=em.createQuery("select r from ServiceRequest r where r.customer.id=:id order by r.serviceRequestedAt desc, r.id desc", com.valor.workflow.ServiceRequest.class).setParameter("id", id).setMaxResults(10).getResultList().stream().map(this::requestView).toList();
        long serviceCount=em.createQuery("select count(r) from ServiceRequest r where r.customer.id=:id", Long.class).setParameter("id", id).getSingleResult();
        long liftCount=em.createQuery("select count(l) from Lift l where l.building.customer.id=:id", Long.class).setParameter("id", id).getSingleResult();
        long buildingCount=em.createQuery("select count(b) from Building b where b.customer.id=:id", Long.class).setParameter("id", id).getSingleResult();
        boolean active=profile.user.isActive() && profile.active;
        return new AdminCustomerController.AdminCustomerDetail(profile.user.getId(), id, profile.user.getEmail(), profile.user.getPhone(), profile.fullName,
            profile.alternatePhone, profile.companyName, profile.address, active, profile.status, profile.createdAt, profile.updatedAt,
            buildingCount, liftCount, serviceCount, buildings, lifts, requests);
    }
    private WorkflowDtos.RequestView requestView(com.valor.workflow.ServiceRequest r) {
        return new WorkflowDtos.RequestView(r.getId(), r.getServiceId(), r.getCustomer().getId(), r.getLift().getId(),
            r.getTitle(), r.getDescription(), r.getIssueCategory(), r.getPriority(), r.getStatus(), r.getServiceType(),
            r.getCustomerRemarks(), r.getTechnicianRemarks(), r.getServiceRequestedAt(), r.getPreferredVisitDate(),
            r.getPreferredTimeSlot(), r.getInternalAdminNotes(), r.getCompletedAt(), r.getEstimatedCompletionMinutes(),
            r.getCreatedAt(), r.getUpdatedAt());
    }
    private String clean(String value) { var result=value==null?null:value.trim(); return result==null||result.isEmpty()?null:result; }
}

class CustomerNotFoundException extends RuntimeException {}
