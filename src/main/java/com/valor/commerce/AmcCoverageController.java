package com.valor.commerce;

import com.valor.response.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class AmcCoverageController {
    private final AmcCoverageService coverage;
    AmcCoverageController(AmcCoverageService coverage){this.coverage=coverage;}
    private <T> ApiResponse<T> ok(T value){return ApiResponse.success("Maintenance coverage",value,200);}
    @GetMapping("/admin/amc-contracts/{id}/entitlements") ApiResponse<List<Map<String,Object>>> terms(@PathVariable long id){return ok(coverage.entitlements(id));}
    @PutMapping("/admin/amc-contracts/{id}/entitlements") ApiResponse<Map<String,Object>> save(@PathVariable long id,@RequestBody AmcCoverageService.Entitlement input){return ok(coverage.saveEntitlement(id,input));}
    @GetMapping("/lifts/{id}/amc-coverage") ApiResponse<Map<String,Object>> preview(@PathVariable long id,@RequestParam String serviceType,@RequestParam String scope,@RequestParam LocalDate serviceDate){return ok(coverage.preview(id,serviceType,scope,serviceDate));}
    @GetMapping("/service-requests/{id}/coverage-decisions") ApiResponse<List<Map<String,Object>>> decisions(@PathVariable long id){return ok(coverage.decisions(id));}
    @PostMapping("/admin/service-requests/{id}/coverage-decisions") ApiResponse<Map<String,Object>> capture(@PathVariable long id,@RequestBody AmcCoverageService.Decision input){return ok(coverage.captureAdmin(id,input));}
}
