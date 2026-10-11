package com.valor.auth;

import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/technician-applications")
class TechnicianApplicationAdminController {
    private final TechnicianApplicationService service;
    TechnicianApplicationAdminController(TechnicianApplicationService service) { this.service = service; }
    @GetMapping ApiResponse<List<TechnicianApplicationService.ApplicationView>> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return ApiResponse.success("Technician applications", service.adminList(page,size), 200); }
    @PutMapping("/{id}/status") ApiResponse<TechnicianApplicationService.ApplicationView> status(@PathVariable Long id,@Valid @RequestBody Status input) { return ApiResponse.success("Technician application updated", service.adminStatus(id,input.status()), 200); }
    @PutMapping("/{id}/documents/{documentId}/review") ApiResponse<TechnicianApplicationService.ApplicationView> reviewDocument(@PathVariable Long id,@PathVariable Long documentId,@Valid @RequestBody Status input) { return ApiResponse.success("Technician document reviewed", service.reviewDocument(id,documentId,input.status(),input.reason()), 200); }
    @PutMapping("/{id}/meeting") ApiResponse<TechnicianApplicationService.ApplicationView> meeting(@PathVariable Long id,@Valid @RequestBody TechnicianApplicationController.Meeting input) { return ApiResponse.success("Technician meeting updated", service.meeting(id,input), 200); }
    @PostMapping("/{id}/meeting/complete") ApiResponse<TechnicianApplicationService.ApplicationView> complete(@PathVariable Long id,@RequestBody(required=false) Status input) { return ApiResponse.success("Technician meeting completed", service.completeMeeting(id,input == null ? null : input.reason()), 200); }
    @GetMapping("/{id}/documents/{documentId}/download") ResponseEntity<Resource> download(@PathVariable Long id,@PathVariable Long documentId) { var file=service.downloadDocument(id,documentId); return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).body(file.resource()); }
    record Status(@NotBlank String status, String reason) {}
}
