package com.valor.workflow;

import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers/me/lifts")
class ServiceCodeController {
    private final ServiceCodeService serviceCodes;
    ServiceCodeController(ServiceCodeService serviceCodes) { this.serviceCodes = serviceCodes; }
    @GetMapping("/{liftId}/service-code") ApiResponse<ServiceCodeService.CodeView> view(@PathVariable Long liftId) { return ok(serviceCodes.customerCode(liftId, false)); }
    @PostMapping("/{liftId}/service-code/regenerate") ApiResponse<ServiceCodeService.CodeView> regenerate(@PathVariable Long liftId) { return ok(serviceCodes.customerCode(liftId, true)); }
    private static <T> ApiResponse<T> ok(T value) { return ApiResponse.success("Success", value, 200); }
}

@RestController
@RequestMapping("/api/v1/technician/me/jobs")
class TechnicianServiceCodeController {
    private final ServiceCodeService serviceCodes;
    TechnicianServiceCodeController(ServiceCodeService serviceCodes) { this.serviceCodes = serviceCodes; }
    @GetMapping("/{requestId}/service-code")
    ApiResponse<TechnicianServiceCodeVerificationView> state(@PathVariable Long requestId, @RequestParam ServiceCodeAction action) {
        ServiceCodeService.CodeView result = serviceCodes.technicianState(requestId, action);
        return ApiResponse.success("Success", result == null ? null : new TechnicianServiceCodeVerificationView(result.liftId(), result.status(), result.lockedUntil(), result.attempts(), result.verifiedAt()), 200);
    }
    @PostMapping("/{requestId}/service-code/request")
    ApiResponse<TechnicianServiceCodeVerificationView> send(@PathVariable Long requestId, @RequestParam ServiceCodeAction action) {
        ServiceCodeService.CodeView result = serviceCodes.sendToCustomer(requestId, action);
        return ApiResponse.success("Code sent to customer Notifications", new TechnicianServiceCodeVerificationView(result.liftId(), result.status(), result.lockedUntil(), result.attempts(), result.verifiedAt()), 200);
    }
    @PostMapping("/{requestId}/service-code/verify")
    ApiResponse<TechnicianServiceCodeVerificationView> verify(@PathVariable Long requestId,
            @Valid @RequestBody TechnicianServiceCodeVerificationRequest input) {
        ServiceCodeAction action;
        if ("START".equals(input.action())) action = ServiceCodeAction.START;
        else if ("COMPLETE".equals(input.action())) action = ServiceCodeAction.COMPLETE;
        else throw new IllegalArgumentException("Unsupported service-code verification action");
        ServiceCodeService.CodeView result = serviceCodes.verify(requestId, action, input.code());
        return ApiResponse.success("Success", new TechnicianServiceCodeVerificationView(result.liftId(), result.status(), result.lockedUntil(), result.attempts(), result.verifiedAt()), 200);
    }
}

record TechnicianServiceCodeVerificationRequest(@NotBlank String action, @NotBlank String code) {}
record TechnicianServiceCodeVerificationView(Long liftId, String status, java.time.LocalDateTime lockedUntil,
        Integer attempts, java.time.LocalDateTime verifiedAt) {}
