package com.valor.commerce;

import com.valor.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1")
class CompanyCashLedgerController {
    private final CompanyCashLedgerService service;
    CompanyCashLedgerController(CompanyCashLedgerService service) { this.service = service; }

    @GetMapping("/technician/me/company-cash/summary")
    ApiResponse<CompanyCashLedgerService.Summary> summary() {
        return ApiResponse.success("Company cash summary", service.summary(), 200);
    }
    @GetMapping("/technician/me/company-cash/receipts")
    ApiResponse<List<CompanyCashLedgerService.Receipt>> receipts() {
        return ApiResponse.success("Company cash receipts", service.receipts(), 200);
    }
    @GetMapping("/technician/me/company-cash/remittances")
    ApiResponse<List<CompanyCashLedgerService.Remittance>> remittances() {
        return ApiResponse.success("Company cash remittances", service.remittances(), 200);
    }
    @PostMapping("/technician/me/company-cash/remittances")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<CompanyCashLedgerService.Remittance> submit(@RequestBody CompanyCashLedgerService.Submit input) {
        return ApiResponse.success("Remittance submitted for verification", service.submit(input), 201);
    }
    @GetMapping("/admin/company-cash/remittances")
    ApiResponse<List<CompanyCashLedgerService.Remittance>> adminQueue(@RequestParam(required = false) String status) {
        return ApiResponse.success("Cash remittance queue", service.adminQueue(status), 200);
    }
    @PostMapping("/admin/company-cash/remittances/{id}/review")
    ApiResponse<CompanyCashLedgerService.Remittance> review(@PathVariable long id, @RequestBody CompanyCashLedgerService.Review input) {
        return ApiResponse.success("Cash remittance reviewed", service.review(id, input), 200);
    }
}
