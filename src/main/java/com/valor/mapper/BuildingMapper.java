package com.valor.mapper;

import com.valor.entity.Building;
import com.valor.entity.Customer;
import com.valor.request.BuildingRequest;
import com.valor.response.BuildingResponse;

public final class BuildingMapper {

    private BuildingMapper() {
    }

    public static Building toEntity(BuildingRequest request) {
        Building building = new Building();
        building.setBuildingName(request.buildingName());
        building.setBuildingType(request.buildingType());
        building.setAddress(request.address());
        building.setCity(request.city());
        building.setState(request.state());
        building.setPincode(request.pincode());
        building.setNumberOfLifts(request.numberOfLifts());
        building.setEmergencyContactName(request.emergencyContactName());
        building.setEmergencyContactPhone(request.emergencyContactPhone());
        building.setStatus(request.status());
        Customer customer = new Customer();
        customer.setId(request.customerId());
        building.setCustomer(customer);
        return building;
    }

    public static BuildingResponse toResponse(Building building) {
        return new BuildingResponse(
                building.getId(),
                building.getCustomer() == null ? null : building.getCustomer().getId(),
                building.getBuildingName(),
                building.getBuildingType(),
                building.getAddress(),
                building.getCity(),
                building.getState(),
                building.getPincode(),
                building.getNumberOfLifts(),
                building.getEmergencyContactName(),
                building.getEmergencyContactPhone(),
                building.getStatus()
        );
    }
}
