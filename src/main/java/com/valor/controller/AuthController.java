package com.valor.controller;

import com.valor.dto.SendOtpRequest;
import com.valor.dto.CustomerDto;
import com.valor.dto.LoginRequest;
import com.valor.dto.VerifyOtpRequest;
import com.valor.entity.Customer;
import com.valor.entity.Admin;
import com.valor.entity.Technician;
import com.valor.repository.AdminRepository;
import com.valor.repository.TechnicianRepository;
import com.valor.request.AuthRequest;
import com.valor.response.ApiResponse;
import com.valor.response.AuthResponse;
import com.valor.response.CustomerProfileResponse;
import com.valor.response.LoginResponse;
import com.valor.response.OtpResponse;
import com.valor.security.JwtTokenProvider;
import com.valor.service.CustomerAccountService;
import com.valor.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authentication entry points. The customer aliases keep the mobile API compatible. */
@RestController
@RequestMapping("/api")
@Tag(name = "Authentication", description = "Customer registration, login, and profile APIs")
public class AuthController {

    private final CustomerAccountService customerService;
    private final JwtTokenProvider jwtTokenProvider;
    private final OtpService otpService;
    private final AdminRepository adminRepository;
    private final TechnicianRepository technicianRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(CustomerAccountService customerService, JwtTokenProvider jwtTokenProvider,
                          OtpService otpService, AdminRepository adminRepository,
                          TechnicianRepository technicianRepository, PasswordEncoder passwordEncoder) {
        this.customerService = customerService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.otpService = otpService;
        this.adminRepository = adminRepository;
        this.technicianRepository = technicianRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Operation(summary = "Register customer")
    @PostMapping({"/auth/register", "/customers/signup"})
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> register(@Valid @RequestBody CustomerDto request) {
        Customer customer = customerService.registerCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Customer registered successfully", CustomerProfileResponse.from(customer), HttpStatus.CREATED.value()));
    }

    @Operation(summary = "Login customer")
    @PostMapping({"/auth/login", "/customers/signin"})
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        Customer customer = customerService.loginCustomer(request.getEmail(), request.getPassword());
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid email or password", HttpStatus.UNAUTHORIZED.value()));
        }

        UserDetails principal = User.withUsername(customer.getEmail())
                .password(customer.getPassword())
                .roles(customer.getRole().name())
                .build();
        AuthResponse response = AuthResponse.builder()
                .accessToken(jwtTokenProvider.generateToken(principal))
                .tokenType("Bearer")
                .role(customer.getRole().name())
                .email(customer.getEmail())
                .build();
        return ResponseEntity.ok(ApiResponse.success("Login successful", response, HttpStatus.OK.value()));
    }

    @Operation(summary = "Login admin")
    @PostMapping("/auth/admin/login")
    public ResponseEntity<ApiResponse<AuthResponse>> adminLogin(@Valid @RequestBody AuthRequest request) {
        Admin admin = adminRepository.findByEmail(request.getEmail()).orElse(null);
        if (admin == null || !Boolean.TRUE.equals(admin.getActive())
                || !passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            return unauthorized();
        }
        return authenticatedResponse(admin.getEmail(), admin.getRole().name());
    }

    @Operation(summary = "Login technician")
    @PostMapping("/auth/technician/login")
    public ResponseEntity<ApiResponse<AuthResponse>> technicianLogin(@Valid @RequestBody AuthRequest request) {
        Technician technician = technicianRepository.findByEmail(request.getEmail()).orElse(null);
        if (technician == null || !passwordEncoder.matches(request.getPassword(), technician.getPassword())) {
            return unauthorized();
        }
        return authenticatedResponse(technician.getEmail(), technician.getRole().name());
    }

    private ResponseEntity<ApiResponse<AuthResponse>> authenticatedResponse(String email, String role) {
        UserDetails principal = User.withUsername(email).password("").roles(role).build();
        AuthResponse response = AuthResponse.builder()
                .accessToken(jwtTokenProvider.generateToken(principal))
                .tokenType("Bearer")
                .role(role)
                .email(email)
                .build();
        return ResponseEntity.ok(ApiResponse.success("Login successful", response, HttpStatus.OK.value()));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password", HttpStatus.UNAUTHORIZED.value()));
    }

    @Operation(summary = "Send OTP for mobile login")
    @PostMapping("/auth/send-otp")
    public ResponseEntity<ApiResponse<OtpResponse>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        OtpResponse response = otpService.sendOtp(request.mobileNumber());
        return ResponseEntity.ok(ApiResponse.success("OTP sent successfully", response, HttpStatus.OK.value()));
    }

    @Operation(summary = "Verify OTP and issue JWT")
    @PostMapping("/auth/verify-otp")
    public ResponseEntity<ApiResponse<LoginResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        LoginResponse response = otpService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response, HttpStatus.OK.value()));
    }

    @Operation(summary = "Get the authenticated customer's profile")
    @GetMapping("/auth/me")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> me(Authentication authentication) {
        String identifier = authentication.getName();
        Customer customer = customerService.getCustomerByEmail(identifier)
            .or(() -> customerService.getCustomerByPhone(identifier))
                .orElseThrow(() -> new IllegalArgumentException("Authenticated customer no longer exists"));
        return ResponseEntity.ok(ApiResponse.success(
                "Profile fetched successfully", CustomerProfileResponse.from(customer), HttpStatus.OK.value()));
    }
}
