package com.valor.auth;

import com.valor.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.persistence.EntityManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController @RequestMapping("/api/v1")
@Transactional
class CustomerProfileController {
    private final AssetIdentityAccess identities;private final CustomerRepo customers;private final EntityManager em;private final AuthService auth;
    CustomerProfileController(AssetIdentityAccess identities,CustomerRepo customers,EntityManager em,AuthService auth){this.identities=identities;this.customers=customers;this.em=em;this.auth=auth;}
    record ProfileWrite(@NotBlank @Size(max=160) String fullName,@Size(max=20) String alternatePhone,
                        @Size(max=200) String companyName,@Size(max=500) String address) implements AuthDtos.StrictInput {}
    private CustomerProfile profile(){User u=identities.actor();if(u.getRole()!=Role.CUSTOMER)throw new org.springframework.security.access.AccessDeniedException("Access denied");identities.requireActiveCustomerUser(u);return customers.findByUserId(u.getId()).orElseThrow();}
    record TechnicianCounts(long districtCount,long stateCount) {}
    private AuthDtos.CustomerSummary view(CustomerProfile p){if(p.referralCode==null)p.referralCode=auth.generateReferralCode(p.fullName);return new AuthDtos.CustomerSummary(p.id,p.fullName,p.alternatePhone,p.companyName,p.address,p.status,p.active,p.referralCode);}
    @Operation(operationId="getCustomerProfile") @GetMapping("/customers/me")
    ApiResponse<AuthDtos.CustomerSummary> get(){return ApiResponse.success("Success",view(profile()),200);}
    @Operation(operationId="updateCustomerProfile") @PutMapping("/customers/me")
    ApiResponse<AuthDtos.CustomerSummary> update(@Valid @RequestBody ProfileWrite input){CustomerProfile p=profile();p.fullName=input.fullName();p.alternatePhone=input.alternatePhone();p.companyName=input.companyName();p.address=input.address();return ApiResponse.success("Success",view(p),200);}
    @Operation(operationId="getCustomerTechnicianCounts") @GetMapping("/customers/me/technician-counts")
    ApiResponse<TechnicianCounts> technicianCounts(@RequestParam(required=false) String district,@RequestParam(required=false) String state){
        profile();
        long districtCount=countTechnicians(district);
        long stateCount=countTechnicians(state);
        return ApiResponse.success("Success",new TechnicianCounts(districtCount,stateCount),200);
    }
    private long countTechnicians(String area){
        if(area==null||area.isBlank())return 0;
        return em.createQuery("select count(t) from TechnicianProfile t join t.user u where t.active=true and u.active=true and u.role=com.valor.auth.Role.TECHNICIAN and lower(t.assignedArea)=lower(:area)",Long.class).setParameter("area",area.trim()).getSingleResult();
    }
}
