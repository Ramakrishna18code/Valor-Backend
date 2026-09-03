package com.valor.controller;

import com.valor.entity.Lift;
import com.valor.mapper.LiftMapper;
import com.valor.request.LiftRequest;
import com.valor.response.ApiResponse;
import com.valor.response.LiftResponse;
import com.valor.service.LiftService;
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
@RequestMapping("/api/lifts")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Lifts", description = "Lift registration and management")
public class LiftController {

    private final LiftService liftService;

    public LiftController(LiftService liftService) {
        this.liftService = liftService;
    }

    @Operation(summary = "Register lift")
    @PostMapping
    public ResponseEntity<ApiResponse<LiftResponse>> createLift(@RequestBody LiftRequest request) {
        Lift created = liftService.createLift(LiftMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Lift registered successfully", LiftMapper.toResponse(created), HttpStatus.CREATED.value()));
    }

    @Operation(summary = "Get lift details")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LiftResponse>> getLift(@PathVariable Long id) {
        Lift lift = liftService.getLiftById(id);
        return ResponseEntity.ok(ApiResponse.success("Lift fetched successfully", LiftMapper.toResponse(lift), HttpStatus.OK.value()));
    }

    @Operation(summary = "List all lifts")
    @GetMapping
    public ResponseEntity<ApiResponse<List<LiftResponse>>> getAllLifts() {
        List<LiftResponse> response = liftService.getAllLifts().stream()
                .map(LiftMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Lifts fetched successfully", response, HttpStatus.OK.value()));
    }

    @Operation(summary = "Get customer lifts")
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<LiftResponse>>> getCustomerLifts(@PathVariable Long customerId) {
        List<Lift> lifts = liftService.getCustomerLifts(customerId);
        return ResponseEntity.ok(ApiResponse.success("Customer lifts fetched", lifts.stream().map(LiftMapper::toResponse).collect(Collectors.toList()), HttpStatus.OK.value()));
    }

    @Operation(summary = "Search lift")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<LiftResponse>>> searchLifts(
            @RequestParam String term,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<LiftResponse> response = liftService.searchLifts(term, pageable).map(LiftMapper::toResponse);
        return ResponseEntity.ok(ApiResponse.success("Search completed", response, HttpStatus.OK.value()));
    }

    @Operation(summary = "Update lift")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LiftResponse>> updateLift(@PathVariable Long id, @RequestBody LiftRequest request) {
        Lift updated = liftService.updateLift(id, LiftMapper.toEntity(request));
        return ResponseEntity.ok(ApiResponse.success("Lift updated successfully", LiftMapper.toResponse(updated), HttpStatus.OK.value()));
    }

    @Operation(summary = "Delete lift")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteLift(@PathVariable Long id) {
        liftService.deleteLift(id);
        return ResponseEntity.ok(ApiResponse.success("Lift deleted successfully", null, HttpStatus.OK.value()));
    }
}
