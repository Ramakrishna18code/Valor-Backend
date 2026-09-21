package com.valor.assets;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.*;

/** Flat API data only: no JPA entity or identity graph is serialized. */
public final class AssetDtos {
    private AssetDtos() {}
    public record BuildingWrite(
            @NotNull @Positive Long customerProfileId,
            @NotBlank @Size(max = 200) String buildingName,
            @Size(max = 80) String buildingType,
            @Size(max = 500) String address,
            @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Size(max = 20) String pincode,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @Size(max = 160) String emergencyContactName,
            @Size(max = 20) String emergencyContactPhone,
            @Size(max = 20) String buildingPreference,
            @Size(max = 20) String status) {
        @JsonAnySetter
        public void rejectUnknown(String field, JsonNode value) {
            throw new IllegalArgumentException("Unsupported asset field");
        }
    }

    public record CustomerBuildingWrite(@NotBlank @Size(max=200) String buildingName,
        @Size(max=80) String buildingType,@Size(max=500) String address,@Size(max=100) String city,
        @Size(max=100) String state,@Size(max=20) String pincode,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @Size(max=160) String emergencyContactName,
        @Size(max=20) String emergencyContactPhone,
        @Size(max=20) String buildingPreference) {
        @JsonAnySetter public void rejectUnknown(String field,JsonNode value){throw new IllegalArgumentException("Unsupported asset field");}
    }

    public record LiftWrite(
            @NotNull @Positive Long buildingId,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 80) String liftNumber,
            @Size(max = 120) String liftType,
            @Size(max = 120) String model,
            @Size(max = 120) String manufacturer,
            @PositiveOrZero Integer capacity,
            @PositiveOrZero Integer floorCount,
            LiftDoorType doorType,
            @Size(max = 120) String serialNumber,
            LocalDate installationDate,
            @Size(max = 200) String location,
            LiftStatus currentStatus,
            @Size(max = 80) String warrantyStatus,
            LocalDate warrantyStartDate,
            LocalDate warrantyEndDate,
            LocalDate lastMaintenanceDate,
            LocalDate nextMaintenanceDate,
            @Schema(type="integer",format="int32",minimum="0",maximum="100") @PositiveOrZero @Max(100) Byte healthScore,
            @Size(max = 200) String machineRoom,
            @Size(max = 255) String qrCode,
            String specifications) {
        @JsonAnySetter
        public void rejectUnknown(String field, JsonNode value) {
            throw new IllegalArgumentException("Unsupported asset field");
        }
    }

    public record AmcWrite(
            @NotNull @Positive Long liftId,
            @Size(max = 80) String amcNumber,
            @NotBlank @Size(max = 80) String plan,
            String coverageDetails,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            LocalDate renewalDate) {
        @JsonAnySetter
        public void rejectUnknown(String field, JsonNode value) {
            throw new IllegalArgumentException("Unsupported asset field");
        }
    }

    public record AmcRenew(
            @NotBlank @Size(max = 80) String plan,
            String coverageDetails,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            LocalDate renewalDate) {
        @JsonAnySetter
        public void rejectUnknown(String field, JsonNode value) {
            throw new IllegalArgumentException("Unsupported asset field");
        }
    }

    public record BuildingView(
            Long id,
            Long customerProfileId,
            String buildingName,
            String buildingType,
            String address,
            String city,
            String state,
            String pincode,
            BigDecimal latitude,
            BigDecimal longitude,
            String emergencyContactName,
            String emergencyContactPhone,
            String buildingPreference,
            String status,
            boolean isActive, long activeLiftCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record LiftView(
            Long id,
            Long buildingId,
            String name,
            String liftNumber,
            String liftType,
            String model,
            String manufacturer,
            Integer capacity,
            Integer floorCount,
            LiftDoorType doorType,
            String serialNumber,
            LocalDate installationDate,
            String location,
            LiftStatus currentStatus,
            String warrantyStatus,
            LocalDate warrantyStartDate,
            LocalDate warrantyEndDate,
            LocalDate lastMaintenanceDate,
            LocalDate nextMaintenanceDate,
            @Schema(type="integer",format="int32",minimum="0",maximum="100") Byte healthScore,
            String machineRoom,
            String qrCode,
            String specifications,
            boolean isActive, @Schema(allowableValues={"ACTIVE","NON_AMC"}) String amcCoverage, LocalDate asOfDate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record LiftBrandModels(String brand, java.util.List<String> models) {}
    public record LiftCatalog(java.util.List<String> liftTypes, java.util.List<LiftBrandModels> brands) {}

    public record AmcView(
            Long id,
            Long liftId,
            String amcNumber,
            String plan,
            String coverageDetails,
            LocalDate startDate,
            LocalDate endDate,
            AmcStatus status,
            LocalDate renewalDate,
            LocalDateTime lastReminderSentAt,
            Integer renewalCount,
            boolean covered, LocalDate asOfDate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

}
