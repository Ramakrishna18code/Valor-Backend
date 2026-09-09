package com.valor.auth;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

final class AuthDtos {
    interface StrictInput {
        @JsonAnySetter default void rejectUnknown(String key, JsonNode value) { throw new IllegalArgumentException("Unsupported field"); }
    }
    record Registration(String email, String phone,
        @Schema(accessMode=Schema.AccessMode.WRITE_ONLY,format="password") @Size(max=72) String password,
        @NotBlank @Size(max=160) String fullName, @Size(max=20) String alternatePhone,
        @Size(max=200) String companyName,@Size(max=500) String address) implements StrictInput {}
    record CustomerLogin(@NotBlank String identity,
        @Schema(accessMode=Schema.AccessMode.WRITE_ONLY,format="password") @NotBlank String password) implements StrictInput {}
    record EmailLogin(@NotBlank String email,
        @Schema(accessMode=Schema.AccessMode.WRITE_ONLY,format="password") @NotBlank String password) implements StrictInput {}
    record OtpSend(@NotBlank String phone) implements StrictInput {}
    record OtpVerify(@NotBlank String phone, @NotBlank String otp, @NotNull @Positive Long requestId) implements StrictInput {}
    record Refresh(@NotBlank String refreshToken) implements StrictInput {}
    record CustomerSummary(Long id,String fullName,String alternatePhone,String companyName,String address,String status,boolean active) {}
    record TechnicianSummary(Long id,String employeeId,String assignedArea,String specialization,
        @Schema(allowableValues={"AVAILABLE","BUSY","OFF_DUTY","ON_LEAVE"}) String availabilityStatus,boolean active) {}
    record CurrentUser(Long userId,Role role,String email,String phone,
        @Schema(nullable=true) CustomerSummary customerProfile,@Schema(nullable=true) TechnicianSummary technicianProfile) {}
    record Authentication(String accessToken,String refreshToken,Role role,Long userId,
        @Schema(nullable=true) CustomerSummary customerProfile,@Schema(nullable=true) TechnicianSummary technicianProfile) {}
    record Rotation(String accessToken,String refreshToken) {}
    record OtpSent(Long requestId,LocalDateTime expiresAt,boolean developmentOnly,
        @Schema(nullable=true,description="Development/test profiles only; never a production delivery claim") String otp) {}
}
