package com.valor.response;

public record BuildingResponse(
        Long id,
        Long customerId,
        String buildingName,
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
