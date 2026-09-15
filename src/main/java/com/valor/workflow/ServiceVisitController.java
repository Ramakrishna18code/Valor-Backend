package com.valor.workflow;

import com.valor.response.ApiResponse;
import com.valor.auth.AssetIdentityAccess;
import com.valor.auth.User;
import java.time.LocalDate;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static com.valor.workflow.ServiceVisitDtos.*;

@RestController
@RequestMapping("/api/v1")
class ServiceVisitController {
    private final ServiceVisitService service;
        private final AssetIdentityAccess identities;
        ServiceVisitController(ServiceVisitService service, AssetIdentityAccess identities) { this.service = service; this.identities = identities; }

    @GetMapping("/admin/service-visits")
    ApiResponse<PageView<VisitView>> adminList(@RequestParam(required=false) LocalDate fromDate,
            @RequestParam(required=false) LocalDate toDate, @RequestParam(required=false) Long technicianProfileId,
            @RequestParam(required=false) VisitStatus status, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return ok(service.adminList(fromDate, toDate, technicianProfileId, status, page, size)); }

    @PostMapping("/admin/service-visits")
    ApiResponse<VisitView> create(@Valid @RequestBody VisitCreateRequest input) { return ok(service.create(input)); }

    @GetMapping("/admin/service-visits/{id}")
    ApiResponse<VisitView> adminDetail(@PathVariable Long id) { return ok(service.adminDetail(id)); }

    @PutMapping("/admin/service-visits/{id}")
    ApiResponse<VisitView> update(@PathVariable Long id, @Valid @RequestBody VisitUpdateRequest input) { return ok(service.update(id, input)); }

    @PostMapping("/admin/service-visits/{id}/cancel")
    ApiResponse<VisitView> adminCancel(@PathVariable Long id, @Valid @RequestBody VisitCancelRequest input) { return ok(service.cancel(id, input.reason(), actor())); }

    @GetMapping("/technician/me/visits")
    ApiResponse<PageView<VisitView>> technicianList(@RequestParam(required=false) LocalDate fromDate,
            @RequestParam(required=false) LocalDate toDate, @RequestParam(required=false) VisitStatus status,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ok(service.technicianList(fromDate, toDate, status, page, size));
    }

    @GetMapping("/technician/me/visits/{id}")
    ApiResponse<VisitView> technicianDetail(@PathVariable Long id) { return ok(service.technicianDetail(id)); }

    @PutMapping("/technician/me/visits/{id}/status")
    ApiResponse<VisitView> progress(@PathVariable Long id, @Valid @RequestBody VisitProgressRequest input) { return ok(service.progress(id, input)); }

    @PostMapping("/technician/me/visits/{id}/reschedule-requests")
    ApiResponse<VisitChangeRequestView> rescheduleRequest(@PathVariable Long id, @Valid @RequestBody ChangeRequestCreate input) { return ok(service.requestReschedule(id, input)); }

    @PostMapping("/technician/me/visits/{id}/additional-visit-requests")
    ApiResponse<VisitChangeRequestView> additionalRequest(@PathVariable Long id, @Valid @RequestBody ChangeRequestCreate input) { return ok(service.requestAdditional(id, input)); }

    @PostMapping("/technician/me/visits/{id}/cancel")
    ApiResponse<VisitView> technicianCancel(@PathVariable Long id, @Valid @RequestBody VisitCancelRequest input) { return ok(service.cancel(id, input.reason(), actor())); }

    @GetMapping("/admin/visit-change-requests")
    ApiResponse<PageView<VisitChangeRequestView>> changeList(@RequestParam(required=false) VisitChangeRequestType type,
            @RequestParam(required=false) VisitChangeRequestStatus status, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return ok(service.changeList(type, status, page, size)); }

    @PostMapping("/admin/visit-change-requests/{id}/approve")
    ApiResponse<VisitView> approve(@PathVariable Long id, @Valid @RequestBody ChangeRequestDecision input) { return ok(service.approve(id, input)); }

    @PostMapping("/admin/visit-change-requests/{id}/reject")
    ApiResponse<VisitChangeRequestView> reject(@PathVariable Long id, @Valid @RequestBody VisitCancelRequest input) { return ok(service.reject(id, input.reason())); }

    @GetMapping("/customers/me/visits")
    ApiResponse<PageView<VisitView>> customerList(@RequestParam(required=false) Long serviceRequestId,
            @RequestParam(required=false) VisitStatus status, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return ok(service.customerList(serviceRequestId, status, page, size)); }

        private User actor() { return identities.actor(); }
    private <T> ApiResponse<T> ok(T value) { return ApiResponse.success("Success", value, 200); }
}
