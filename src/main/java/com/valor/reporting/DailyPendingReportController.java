package com.valor.reporting;

import com.valor.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DailyPendingReportController {
    private final DailyPendingReportService reports;
    DailyPendingReportController(DailyPendingReportService reports) { this.reports = reports; }

    @GetMapping("/api/v1/admin/reports/daily-pending")
    ResponseEntity<ApiResponse<DailyPendingReportService.Report>> current() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Daily pending operations", reports.current(), 200));
    }
}
