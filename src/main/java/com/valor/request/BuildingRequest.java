package com.valor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BuildingRequest(
        @NotNull(message = "Customer id is required") Long customerId,
        @NotBlank(message = "Building name is required") String buildingName,
        String buildingType,
        String address,
        String city,
        String state,
        String pincode,
        Integer numberOfLifts,
        String emergencyContactName,
        String emergencyContactPhone,
        String status
) {
}
