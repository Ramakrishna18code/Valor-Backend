package com.valor.response;

import com.valor.enums.AMCStatus;
import com.valor.enums.LiftStatus;
import java.time.LocalDate;

public record LiftResponse(
        Long id,
        Long customerId,
        Long buildingId,
        String name,
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
        String specifications
) { }