package com.valor.pricing;

import com.valor.response.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class EstimateCatalogueController {
    private final EstimateCatalogueService service;
    public EstimateCatalogueController(EstimateCatalogueService service) { this.service = service; }
    public record Publish(long version) {}
    @GetMapping("/admin/service-estimates") public ApiResponse<?> draft() { return ok(service.draft()); }
    @PutMapping("/admin/service-estimates") public ApiResponse<?> save(@RequestBody EstimateCatalogueService.Write input) { return ok(service.save(input)); }
    @PostMapping("/admin/service-estimates/publish") public ApiResponse<?> publish(@RequestBody Publish input) { return ok(service.publish(input.version())); }
    @GetMapping("/service-estimates") public ApiResponse<?> published() { return ok(service.published()); }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> error(org.springframework.web.server.ResponseStatusException error) {
        return org.springframework.http.ResponseEntity.status(error.getStatusCode()).body(ApiResponse.error(error.getReason(), error.getStatusCode().value()));
    }
    private static <T> ApiResponse<T> ok(T value) { return ApiResponse.success("Success", value, 200); }
}
