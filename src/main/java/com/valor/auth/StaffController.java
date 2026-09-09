package com.valor.auth;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
class StaffController {
    private final StaffService staff;
    StaffController(StaffService staff) { this.staff = staff; }
    record Create(@NotBlank @Size(max=254) String email, @NotBlank @Size(max=72) String password,
                  @NotNull Role role, @Size(max=50) String employeeId, @Size(max=160) String assignedArea,
                  @Size(max=160) String specialization, String availabilityStatus) {
        @JsonAnySetter public void unknown(String key, JsonNode value) { throw new IllegalArgumentException("Unsupported field"); }
    }
    record View(Long userId, String email, Role role, boolean active, Long technicianProfileId,
                String employeeId, String assignedArea, String specialization, String availabilityStatus) {}
    @PostMapping ApiResponse<View> create(@Valid @RequestBody Create input) {
        return ApiResponse.success("Staff created", staff.create(input), 200);
    }
    @DeleteMapping("/{userId}") ApiResponse<View> deactivate(@PathVariable Long userId) {
        return ApiResponse.success("Staff deactivated", staff.deactivate(userId), 200);
    }
}
