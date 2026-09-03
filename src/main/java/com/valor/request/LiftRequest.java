package com.valor.request;

import com.valor.enums.AMCStatus;
import com.valor.enums.LiftStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record LiftRequest(
        @NotBlank(message = "Lift name is required") String name,
        String liftNumber,
        String model,
        String manufacturer,
        Integer capacity,
        Integer floorCount,
        String serialNumber,
        LocalDate installationDate,
        String location,
        LiftStatus currentStatus,
        AMCStatus amcStatus,
        String warrantyStatus,
        LocalDate warrantyStartDate,
        LocalDate warrantyEndDate,
        LocalDate lastMaintenanceDate,
        LocalDate nextMaintenanceDate,
        Integer totalBreakdowns,
        Integer healthScore,
        String machineRoom,
        String qrCode,
        String specifications,
        @NotNull(message = "Customer id is required") Long customerId,
        Long buildingId
) {
}