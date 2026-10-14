package com.valor.installation;

import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.valor.installation.InstallationDtos.*;

@RestController
@RequestMapping("/api/v1/customers/me/installations")
public class InstallationController {
    private final InstallationService service;
    public InstallationController(InstallationService service) { this.service = service; }
    @GetMapping ApiResponse<List<View>> list() { return ok(service.list()); }
    @PostMapping ApiResponse<View> create(@Valid @RequestBody WriteRequest input) { return ok(service.create(input)); }
    @GetMapping("/{id}") ApiResponse<View> read(@PathVariable Long id) { return ok(service.read(id)); }
    @PutMapping("/{id}") ApiResponse<View> update(@PathVariable Long id, @Valid @RequestBody WriteRequest input) { return ok(service.update(id, input)); }
    @PostMapping("/{id}/submit") ApiResponse<View> submit(@PathVariable Long id, @Valid @RequestBody SubmitRequest input) { return ok(service.submit(id, input)); }
    @PostMapping("/{id}/cancel") ApiResponse<View> cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest input) { return ok(service.cancel(id, input)); }
    @PostMapping("/{id}/documents") ApiResponse<View> document(@PathVariable Long id, @Valid @RequestBody DocumentReference input) { return ok(service.addDocument(id, input)); }
    private <T> ApiResponse<T> ok(T value) { return ApiResponse.success("Success", value, 200); }
}
