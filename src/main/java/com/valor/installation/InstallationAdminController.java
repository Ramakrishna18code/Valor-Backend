package com.valor.installation;

import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static com.valor.installation.InstallationDtos.*;
import com.valor.workflow.WorkflowDtos.PageView;

@RestController
@RequestMapping("/api/v1/admin/installations/authorizations")
public class InstallationAdminController {
    private final InstallationService service;
    public InstallationAdminController(InstallationService service) { this.service = service; }
    @GetMapping
    ApiResponse<PageView<View>> list(@RequestParam(required=false) InstallationStatus status, @RequestParam(required=false) String q, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return ApiResponse.success("Installations", service.adminList(status, q, page, size), 200); }
    @GetMapping("/{id}")
    ApiResponse<View> detail(@PathVariable Long id) { return ApiResponse.success("Installation", service.adminRead(id), 200); }
    @PostMapping("/{id}/review") ApiResponse<AuthorizationView> review(@PathVariable Long id, @Valid @RequestBody AuthorizationReviewRequest input) { return ApiResponse.success("Authorization reviewed", service.reviewAuthorization(id, input), 200); }
}
