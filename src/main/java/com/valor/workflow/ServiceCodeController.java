package com.valor.workflow;

import com.valor.response.ApiResponse;
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
