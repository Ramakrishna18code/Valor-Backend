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
    private final AssetIdentityAccess identities;private final CustomerRepo customers;
    CustomerProfileController(AssetIdentityAccess identities,CustomerRepo customers){this.identities=identities;this.customers=customers;}
    record ProfileWrite(@NotBlank @Size(max=160) String fullName,@Size(max=20) String alternatePhone,
                        @Size(max=200) String companyName,@Size(max=500) String address) implements AuthDtos.StrictInput {}
    private CustomerProfile profile(){User u=identities.actor();if(u.getRole()!=Role.CUSTOMER)throw new org.springframework.security.access.AccessDeniedException("Access denied");identities.requireActiveCustomerUser(u);return customers.findByUserId(u.getId()).orElseThrow();}
    private AuthDtos.CustomerSummary view(CustomerProfile p){return new AuthDtos.CustomerSummary(p.id,p.fullName,p.alternatePhone,p.companyName,p.address,p.status,p.active);}
    @Operation(operationId="getCustomerProfile") @GetMapping("/customers/me")
    ApiResponse<AuthDtos.CustomerSummary> get(){return ApiResponse.success("Success",view(profile()),200);}
    @Operation(operationId="updateCustomerProfile") @PutMapping("/customers/me")
    ApiResponse<AuthDtos.CustomerSummary> update(@Valid @RequestBody ProfileWrite input){CustomerProfile p=profile();p.fullName=input.fullName();p.alternatePhone=input.alternatePhone();p.companyName=input.companyName();p.address=input.address();return ApiResponse.success("Success",view(p),200);}
}
