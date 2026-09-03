package com.valor.controller;

import com.valor.entity.Amc;
import com.valor.mapper.AmcMapper;
import com.valor.request.AmcRequest;
import com.valor.response.ApiResponse;
import com.valor.response.AmcResponse;
import com.valor.service.AmcService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag; 
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/amcs")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "AMC", description = "AMC creation, renewal, and tracking")
public class AmcController {

    private final AmcService amcService;

    public AmcController(AmcService amcService) {
        this.amcService = amcService;
    }

    @Operation(summary = "Create AMC")
    @PostMapping
    public ResponseEntity<ApiResponse<AmcResponse>> createAmc(@RequestBody AmcRequest request) {
        Amc amc = AmcMapper.toEntity(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("AMC created successfully", AmcMapper.toResponse(amcService.createAmc(amc)), HttpStatus.CREATED.value()));
    }

    @Operation(summary = "Renew AMC")
    @PutMapping("/{id}/renew")
    public ResponseEntity<ApiResponse<AmcResponse>> renewAmc(@PathVariable Long id, @RequestBody AmcRequest request) {
        Amc amc = AmcMapper.toEntity(request);
        return ResponseEntity.ok(ApiResponse.success("AMC renewed successfully", AmcMapper.toResponse(amcService.renewAmc(id, amc)), HttpStatus.OK.value()));
    }

    @Operation(summary = "Cancel AMC")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancelAmc(@PathVariable Long id) {
        amcService.cancelAmc(id);
        return ResponseEntity.ok(ApiResponse.success("AMC cancelled successfully", null, HttpStatus.OK.value()));
    }

    @Operation(summary = "Get AMC details")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AmcResponse>> getAmc(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("AMC fetched successfully", AmcMapper.toResponse(amcService.getAmcById(id)), HttpStatus.OK.value()));
    }

    @Operation(summary = "List all AMCs")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AmcResponse>>> getAllAmcs() {
        return ResponseEntity.ok(ApiResponse.success("AMCs fetched successfully", amcService.getAllAmcs().stream().map(AmcMapper::toResponse).collect(Collectors.toList()), HttpStatus.OK.value()));
    }

    @Operation(summary = "Search AMC")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<AmcResponse>>> searchAmcs(
            @RequestParam String term,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "startDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<AmcResponse> response = amcService.searchAmcs(term, pageable).map(AmcMapper::toResponse);
        return ResponseEntity.ok(ApiResponse.success("AMC search completed", response, HttpStatus.OK.value()));
    }
}
