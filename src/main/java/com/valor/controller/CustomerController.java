package com.valor.controller;


import com.valor.dto.CustomerProfileUpdateDto;
import com.valor.entity.Customer;
import com.valor.response.ApiResponse;
import com.valor.response.CustomerProfileResponse;
import com.valor.service.CustomerAccountService;
import com.valor.service.BuildingService;
import com.valor.service.ServiceRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import com.valor.mapper.BuildingMapper;
import com.valor.mapper.ServiceRequestMapper;
import com.valor.response.BuildingResponse;
import com.valor.response.ServiceRequestResponse;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers", description = "Customer profile and management APIs")
public class CustomerController {

    private final CustomerAccountService customerService;
    private final BuildingService buildingService;
    private final ServiceRequestService serviceRequestService;

    public CustomerController(CustomerAccountService customerService,
                              BuildingService buildingService,
                              ServiceRequestService serviceRequestService) {
        this.customerService = customerService;
        this.buildingService = buildingService;
        this.serviceRequestService = serviceRequestService;
    }

    @Operation(summary = "Get the authenticated customer's profile")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> currentCustomer(Authentication authentication) {
        Customer customer = customerService.getCustomerByEmail(authentication.getName())
                .or(() -> customerService.getCustomerByPhone(authentication.getName()))
                .orElseThrow(() -> new com.valor.exception.ResourceNotFoundException("Customer not found"));
        return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully", CustomerProfileResponse.from(customer), HttpStatus.OK.value()));
    }

    @Operation(summary = "Get buildings owned by a customer")
    @GetMapping("/{id}/buildings")
    public ResponseEntity<ApiResponse<List<BuildingResponse>>> getCustomerBuildings(@PathVariable Long id) {
        List<BuildingResponse> response = buildingService.getBuildingsByCustomerId(id).stream()
                .map(BuildingMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Buildings fetched successfully", response, HttpStatus.OK.value()));
    }

    @Operation(summary = "Get service requests created by a customer")
    @GetMapping("/{id}/service-requests")
    public ResponseEntity<ApiResponse<List<ServiceRequestResponse>>> getCustomerServiceRequests(@PathVariable Long id) {
        List<ServiceRequestResponse> response = serviceRequestService.getServiceRequestsByCustomerId(id).stream()
                .map(ServiceRequestMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Service requests fetched successfully", response, HttpStatus.OK.value()));
    }

    @Operation(summary = "Get customer profile")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> getCustomer(@PathVariable Long id) {
        Customer customer = customerService.getCustomerById(id)
                .orElseThrow(() -> new com.valor.exception.ResourceNotFoundException("Customer not found"));
        return ResponseEntity.ok(ApiResponse.success("Customer fetched successfully", CustomerProfileResponse.from(customer), HttpStatus.OK.value()));
    }

    @Operation(summary = "Update profile")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerProfileUpdateDto request) {
        Customer customer = customerService.updateProfile(id, request);
        return ResponseEntity.ok(ApiResponse.success("Customer updated successfully", CustomerProfileResponse.from(customer), HttpStatus.OK.value()));
    }

    @Operation(summary = "Delete account")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable Long id) {
        customerService.deleteAccount(id);
        return ResponseEntity.ok(ApiResponse.success("Customer deleted successfully", null, HttpStatus.OK.value()));
    }
}
