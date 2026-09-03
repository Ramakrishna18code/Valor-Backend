package com.valor.controller;

import com.valor.entity.Building;
import com.valor.mapper.BuildingMapper;
import com.valor.request.BuildingRequest;
import com.valor.response.ApiResponse;
import com.valor.response.BuildingResponse;
import com.valor.response.LiftResponse;
import com.valor.service.BuildingService;
import com.valor.service.LiftService;
import com.valor.mapper.LiftMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/buildings")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Buildings", description = "Building registration and management")
public class BuildingController {

    private final BuildingService buildingService;
    private final LiftService liftService;

    public BuildingController(BuildingService buildingService, LiftService liftService) {
        this.buildingService = buildingService;
        this.liftService = liftService;
    }

    @Operation(summary = "Create building")
    @PostMapping
    public ResponseEntity<ApiResponse<BuildingResponse>> createBuilding(@Valid @RequestBody BuildingRequest request) {
        Building building = BuildingMapper.toEntity(request);
        Building created = buildingService.createBuilding(building);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Building created successfully", BuildingMapper.toResponse(created), HttpStatus.CREATED.value()));
    }

    @Operation(summary = "Get building")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BuildingResponse>> getBuilding(@PathVariable Long id) {
        Building building = buildingService.getBuildingById(id);
        return ResponseEntity.ok(ApiResponse.success("Building fetched successfully", BuildingMapper.toResponse(building), HttpStatus.OK.value()));
    }

    @Operation(summary = "Get lifts in a building")
    @GetMapping("/{id}/lifts")
    public ResponseEntity<ApiResponse<List<LiftResponse>>> getBuildingLifts(@PathVariable Long id) {
        List<LiftResponse> response = liftService.getBuildingLifts(id).stream()
                .map(LiftMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Lifts fetched successfully", response, HttpStatus.OK.value()));
    }

    @Operation(summary = "Get buildings by customer")
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<BuildingResponse>>> getCustomerBuildings(@PathVariable Long customerId) {
        List<BuildingResponse> response = buildingService.getBuildingsByCustomerId(customerId).stream()
                .map(BuildingMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Buildings fetched successfully", response, HttpStatus.OK.value()));
    }

    @Operation(summary = "List buildings")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BuildingResponse>>> getAllBuildings() {
        List<BuildingResponse> response = buildingService.getAllBuildings().stream()
                .map(BuildingMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Buildings fetched successfully", response, HttpStatus.OK.value()));
    }

    @Operation(summary = "Update building")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BuildingResponse>> updateBuilding(@PathVariable Long id, @Valid @RequestBody BuildingRequest request) {
        Building building = BuildingMapper.toEntity(request);
        Building updated = buildingService.updateBuilding(id, building);
        return ResponseEntity.ok(ApiResponse.success("Building updated successfully", BuildingMapper.toResponse(updated), HttpStatus.OK.value()));
    }

    @Operation(summary = "Delete building")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBuilding(@PathVariable Long id) {
        buildingService.deleteBuilding(id);
        return ResponseEntity.ok(ApiResponse.success("Building deleted successfully", null, HttpStatus.OK.value()));
    }
}
