package com.valor.auth;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
class StaffController {
    private final StaffService staff;
    StaffController(StaffService staff) { this.staff = staff; }
    @Schema(name="StaffCreateRequest")
    record Create(@NotBlank @Size(max=254) String email,
                  @Schema(accessMode=Schema.AccessMode.WRITE_ONLY, format="password")
                  @NotBlank @Size(max=72) String password,
                  @Schema(implementation=String.class, allowableValues={"ADMIN","TECHNICIAN"}, example="TECHNICIAN")
                  @NotNull Role role,
                  @Schema(description="Required for TECHNICIAN provisioning; inapplicable for ADMIN (omit). Current runtime permits omission.")
                  @Size(max=50) String employeeId,
                  @Schema(description="Required for TECHNICIAN provisioning; inapplicable for ADMIN (omit). Current runtime permits omission.")
                  @Size(max=160) String assignedArea,
                  @Schema(description="Required for TECHNICIAN provisioning; inapplicable for ADMIN (omit). Current runtime permits omission.")
                  @Size(max=160) String specialization,
                  @Schema(allowableValues={"AVAILABLE","BUSY","OFF_DUTY","ON_LEAVE"}, example="AVAILABLE",
                          description="Required for TECHNICIAN provisioning; inapplicable for ADMIN (omit). Current runtime defaults omitted values to AVAILABLE.")
                  String availabilityStatus) {
        @JsonAnySetter public void unknown(String key, JsonNode value) { throw new IllegalArgumentException("Unsupported field"); }
    }
    @Schema(name="StaffResponse")
    record View(Long userId, String email, @Schema(implementation=String.class,allowableValues={"ADMIN","TECHNICIAN"},example="TECHNICIAN") Role role, boolean active, Long technicianProfileId,
                String employeeId, String assignedArea, String specialization, @Schema(allowableValues={"AVAILABLE","BUSY","OFF_DUTY","ON_LEAVE"}) String availabilityStatus) {}
    @Operation(operationId="createStaff")
    @PostMapping ApiResponse<View> create(@Valid @RequestBody Create input) {
        return ApiResponse.success("Staff created", staff.create(input), 200);
    }
    @Operation(operationId="deactivateStaff")
    @DeleteMapping("/{userId}") ApiResponse<View> deactivate(@PathVariable Long userId) {
        return ApiResponse.success("Staff deactivated", staff.deactivate(userId), 200);
    }
}
