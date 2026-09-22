package com.valor.auth;

import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/technician-applications")
class TechnicianApplicationAdminController {
    private final TechnicianApplicationService service;
    TechnicianApplicationAdminController(TechnicianApplicationService service) { this.service = service; }
    @GetMapping ApiResponse<List<TechnicianApplicationService.ApplicationView>> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return ApiResponse.success("Technician applications", service.adminList(page,size), 200); }
    @PutMapping("/{id}/status") ApiResponse<TechnicianApplicationService.ApplicationView> status(@PathVariable Long id,@Valid @RequestBody Status input) { return ApiResponse.success("Technician application updated", service.adminStatus(id,input.status()), 200); }
    record Status(@NotBlank String status) {}
}
