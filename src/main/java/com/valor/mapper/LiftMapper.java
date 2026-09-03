package com.valor.mapper;

import com.valor.entity.Building;
import com.valor.entity.Customer;
import com.valor.entity.Lift;
import com.valor.request.LiftRequest;
import com.valor.response.LiftResponse;

public final class LiftMapper {

    private LiftMapper() {
    }

    public static Lift toEntity(LiftRequest request) {
        Lift lift = new Lift();
        lift.setName(request.name());
        lift.setLiftNumber(request.liftNumber());
        lift.setModel(request.model());
        lift.setManufacturer(request.manufacturer());
        lift.setCapacity(request.capacity());
        lift.setFloorCount(request.floorCount());
        lift.setSerialNumber(request.serialNumber());
        lift.setInstallationDate(request.installationDate());
        lift.setLocation(request.location());
        lift.setCurrentStatus(request.currentStatus());
        lift.setAmcStatus(request.amcStatus());
        lift.setWarrantyStatus(request.warrantyStatus());
        lift.setWarrantyStartDate(request.warrantyStartDate());
        lift.setWarrantyEndDate(request.warrantyEndDate());
        lift.setLastMaintenanceDate(request.lastMaintenanceDate());
        lift.setNextMaintenanceDate(request.nextMaintenanceDate());
        lift.setTotalBreakdowns(request.totalBreakdowns());
        lift.setHealthScore(request.healthScore());
        lift.setMachineRoom(request.machineRoom());
        lift.setQrCode(request.qrCode());
        lift.setSpecifications(request.specifications());
        Customer customer = new Customer();
        customer.setId(request.customerId());
        lift.setCustomer(customer);
        if (request.buildingId() != null) {
            Building building = new Building();
            building.setId(request.buildingId());
            lift.setBuilding(building);
        }
        return lift;
    }

    public static LiftResponse toResponse(Lift lift) {
        return new LiftResponse(
            lift.getId(),
            lift.getCustomer() == null ? null : lift.getCustomer().getId(),
            lift.getBuilding() == null ? null : lift.getBuilding().getId(),
            lift.getName(),
            lift.getLiftNumber(),
            lift.getModel(),
            lift.getManufacturer(),
            lift.getCapacity(),
            lift.getFloorCount(),
            lift.getSerialNumber(),
            lift.getInstallationDate(),
            lift.getLocation(),
            lift.getCurrentStatus(),
            lift.getAmcStatus(),
            lift.getWarrantyStatus(),
            lift.getWarrantyStartDate(),
            lift.getWarrantyEndDate(),
            lift.getLastMaintenanceDate(),
            lift.getNextMaintenanceDate(),
            lift.getTotalBreakdowns(),
            lift.getHealthScore(),
            lift.getMachineRoom(),
            lift.getQrCode(),
            lift.getSpecifications()
        );
    }
}