package com.valor.commerce;

import com.valor.response.ApiResponse;
import com.valor.auth.AuditService;
import java.time.LocalDate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import static com.valor.commerce.AdminFinanceDtos.*;

@RestController
@RequestMapping("/api/v1/admin")
class AdminFinanceController {
    private final AdminFinanceService service;
    private final AuditService audit;
    AdminFinanceController(AdminFinanceService service, AuditService audit) { this.service = service; this.audit = audit; }

    @GetMapping("/transactions")
    ApiResponse<PageView<TransactionView>> transactions(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String sort,
            @RequestParam(required = false) String status, @RequestParam(required = false) Long customerProfileId,
            @RequestParam(required = false) String type, @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo, @RequestParam(required = false) String q) {
        return ApiResponse.success("Transactions", service.transactions(page, size, sort, status, customerProfileId, type, dateFrom, dateTo, q), 200);
    }

    @GetMapping("/transactions/{id}")
    ApiResponse<TransactionView> transaction(@PathVariable String id) { return ApiResponse.success("Transaction", service.transaction(id), 200); }

    @GetMapping("/transactions.csv")
    ResponseEntity<Resource> transactionCsv(@RequestParam(required = false) String sort,
            @RequestParam(required = false) String status, @RequestParam(required = false) Long customerProfileId,
            @RequestParam(required = false) String type, @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo, @RequestParam(required = false) String q) {
        audit.record("TRANSACTION_EXPORT", "TRANSACTION", null, "Exported transaction CSV");
        return csv("transactions.csv", service.transactionsCsv(sort, status, customerProfileId, type, dateFrom, dateTo, q));
    }

    @GetMapping("/reports/{type}")
    ApiResponse<ReportView> report(@PathVariable String type, @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo, @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerProfileId, @RequestParam(required = false) Long technicianProfileId) {
        return ApiResponse.success("Report", service.report(type, dateFrom, dateTo, status, customerProfileId, technicianProfileId), 200);
    }

    @GetMapping("/reports/{type}.csv")
    ResponseEntity<Resource> reportCsv(@PathVariable String type, @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo, @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerProfileId, @RequestParam(required = false) Long technicianProfileId) {
        audit.record("REPORT_EXPORT", "REPORT", type, "Exported report CSV type=" + type);
        return csv(type.toLowerCase() + "-report.csv", service.reportCsv(type, dateFrom, dateTo, status, customerProfileId, technicianProfileId));
    }

    private ResponseEntity<Resource> csv(String filename, byte[] bytes) {
        return ResponseEntity.ok().contentLength(bytes.length).contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(new ByteArrayResource(bytes));
    }
}
