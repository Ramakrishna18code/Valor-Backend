package com.valor.auth;
import io.swagger.v3.oas.annotations.Operation;

import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.util.Set;
import static com.valor.auth.AuthDtos.*;

@RestController @RequestMapping("/api/v1")
class AuthController {
    private final AuthService auth; private final AssetIdentityAccess identities;
    private final CustomerRepo customers; private final TechRepo technicians; private final Environment env;
    AuthController(AuthService auth,AssetIdentityAccess identities,CustomerRepo customers,TechRepo technicians,Environment env) {
        this.auth=auth;this.identities=identities;this.customers=customers;this.technicians=technicians;this.env=env;
    }
    private ApiResponse<Authentication> ok(User user) {
        String[] tokens=auth.session(user);CurrentUser me=summary(user);
        return ApiResponse.success("Authenticated",new Authentication(tokens[0],tokens[1],user.getRole(),user.getId(),me.customerProfile(),me.technicianProfile()),200);
    }
    private CurrentUser summary(User u) {
        CustomerSummary customer=customers.findByUserId(u.getId()).map(p->new CustomerSummary(p.id,p.fullName,p.alternatePhone,p.companyName,p.address,p.status,p.active,p.referralCode)).orElse(null);
        TechnicianSummary technician=technicians.findByUserId(u.getId()).map(p->new TechnicianSummary(p.id,p.employeeId,p.assignedArea,p.specialization,p.availabilityStatus,p.active)).orElse(null);
        return new CurrentUser(u.getId(),u.getRole(),u.getEmail(),u.getPhone(),customer,technician);
    }
    @Operation(operationId="register")
    @PostMapping("/auth/register") ApiResponse<Authentication> register(@Valid @RequestBody Registration r) {
        return ok(auth.registerProfile(r));
    }
    @Operation(operationId="loginCustomer")
    @PostMapping("/auth/login/customer") ApiResponse<Authentication> loginCustomer(@Valid @RequestBody CustomerLogin r) {
        return ok(auth.login(r.identity(),r.password(),Set.of(Role.CUSTOMER)));
    }
    private User emailLogin(EmailLogin r,Set<Role> roles) {
        String email=auth.normEmail(r.email());
        if(email==null||!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Authentication failed");
        return auth.login(email,r.password(),roles);
    }
    @Operation(operationId="loginAdmin")
    @PostMapping("/auth/login/admin") ApiResponse<Authentication> loginAdmin(@Valid @RequestBody EmailLogin r) {return ok(emailLogin(r,Set.of(Role.ADMIN,Role.SUPER_ADMIN)));}
    @Operation(operationId="loginTechnician")
    @PostMapping("/auth/login/technician") ApiResponse<Authentication> loginTechnician(@Valid @RequestBody EmailLogin r) {return ok(emailLogin(r,Set.of(Role.TECHNICIAN)));}
    @Operation(operationId="sendOtp")
    @PostMapping("/auth/otp/send") ApiResponse<OtpSent> sendOtp(@Valid @RequestBody OtpSend r) {
        if(env.acceptsProfiles(Profiles.of("prod")) || !env.acceptsProfiles(Profiles.of("dev","test")))
            throw new IllegalArgumentException("OTP delivery unavailable");
        return ApiResponse.success("Development OTP generated",auth.sendOtpRequest(r.phone()),200);
    }
    @Operation(operationId="resendOtp")
    @PostMapping("/auth/otp/resend") ApiResponse<OtpSent> resendOtp(@Valid @RequestBody OtpResend r) {
        if(env.acceptsProfiles(Profiles.of("prod")) || !env.acceptsProfiles(Profiles.of("dev","test")))
            throw new IllegalArgumentException("OTP delivery unavailable");
        return ApiResponse.success("Development OTP resent",auth.resendOtpRequest(r.phone(),r.requestId()),200);
    }
    @Operation(operationId="verifyOtp")
    @PostMapping("/auth/otp/verify") ApiResponse<Authentication> verifyOtp(@Valid @RequestBody OtpVerify r) {return ok(auth.verifyOtpRequest(r.phone(),r.otp(),r.requestId()));}
    @Operation(operationId="refresh")
    @PostMapping("/auth/refresh") ApiResponse<Rotation> refresh(@Valid @RequestBody Refresh r) {
        String[] tokens=auth.refresh(r.refreshToken());return ApiResponse.success("Token refreshed",new Rotation(tokens[0],tokens[1]),200);
    }
    @Operation(operationId="setPassword")
    @PostMapping("/auth/set-password") ApiResponse<Void> setPassword(@Valid @RequestBody SetPassword r) {
        auth.setPassword(r.token(), r.password()); return ApiResponse.success("Password set", null, 200);
    }
    @Operation(operationId="logout")
    @PostMapping("/auth/logout") ApiResponse<Void> logout(@Valid @RequestBody Refresh r) {auth.logoutOwned(r.refreshToken(),identities.actor().getId());return ApiResponse.success("Logged out",null,200);}
    @Operation(operationId="me")
    @GetMapping("/me") ApiResponse<CurrentUser> me() {return ApiResponse.success("Current user",summary(identities.actor()),200);}
}
