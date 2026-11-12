package com.valor.reporting;

import com.valor.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/service-reminders")
class ServiceReminderController {
    private final ServiceReminderService reminders;
    ServiceReminderController(ServiceReminderService reminders) { this.reminders = reminders; }
    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(message, value, 200));
    }

    @GetMapping("/summary")
    ResponseEntity<ApiResponse<ServiceReminderService.Summary>> summary() { return ok("Service reminders", reminders.summary()); }

    @GetMapping
    ResponseEntity<ApiResponse<ServiceReminderService.Page>> list(
            @RequestParam(defaultValue = "ALL") String tab, @RequestParam(required = false) String q,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "date") String sort,
            @RequestParam(defaultValue = "asc") String direction, @RequestParam(required = false) String bucket,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok("Service reminders", reminders.list(tab, q, from, to, status, sort, direction, bucket, page, size));
    }

    @GetMapping("/settings")
    ResponseEntity<ApiResponse<ServiceReminderService.Settings>> settings() { return ok("Reminder settings", reminders.settings()); }

    @PutMapping("/settings")
    ResponseEntity<ApiResponse<ServiceReminderService.Settings>> updateSettings(@Valid @RequestBody ServiceReminderService.Settings value) {
        return ok("Reminder settings updated", reminders.updateSettings(value));
    }

    @GetMapping("/alerts")
    ResponseEntity<ApiResponse<ServiceReminderService.AlertPage>> alerts(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return ok("Reminder alerts", reminders.alerts(page, size)); }

    @PutMapping("/alerts/{id}/read")
    ResponseEntity<ApiResponse<ServiceReminderService.Alert>> read(@PathVariable long id) { return ok("Reminder read", reminders.readAlert(id)); }

    @PutMapping("/alerts/{id}/resolve")
    ResponseEntity<ApiResponse<ServiceReminderService.Alert>> resolve(@PathVariable long id) { return ok("Reminder resolved", reminders.resolveAlert(id)); }

    @GetMapping("/reports")
    ResponseEntity<ApiResponse<List<ServiceReminderService.DailyReport>>> reports() { return ok("Daily reminder reports", reminders.reports()); }

    @Operation(operationId = "downloadServiceReminderCsv")
    @GetMapping(value = "/reports.csv", produces = "text/csv")
    ResponseEntity<String> csv() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=service-reminder-reports.csv")
                .contentType(new MediaType("text", "csv")).body(reminders.reportCsv());
    }
}
