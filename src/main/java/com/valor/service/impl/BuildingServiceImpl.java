package com.valor.service.impl;

import com.valor.entity.Building;
import com.valor.entity.Customer;
import com.valor.exception.ResourceNotFoundException;
import com.valor.repository.BuildingRepository;
import com.valor.repository.CustomerRepository;
import com.valor.service.BuildingService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BuildingServiceImpl implements BuildingService {

    private final BuildingRepository buildingRepository;
    private final CustomerRepository customerRepository;

    public BuildingServiceImpl(BuildingRepository buildingRepository, CustomerRepository customerRepository) {
        this.buildingRepository = buildingRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    public Building createBuilding(Building building) {
        Long customerId = building.getCustomer() == null ? null : building.getCustomer().getId();
        if (customerId == null) {
            throw new IllegalArgumentException("Customer id is required");
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        building.setCustomer(customer);
        if (building.getNumberOfLifts() == null) building.setNumberOfLifts(0);
        if (building.getStatus() == null) building.setStatus("ACTIVE");
        return buildingRepository.save(building);
    }

    @Override
    public Building updateBuilding(Long id, Building building) {
        Building existing = buildingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
        existing.setBuildingName(building.getBuildingName());
        existing.setBuildingType(building.getBuildingType());
        existing.setAddress(building.getAddress());
        existing.setCity(building.getCity());
        existing.setState(building.getState());
        existing.setPincode(building.getPincode());
        existing.setNumberOfLifts(building.getNumberOfLifts());
        existing.setEmergencyContactName(building.getEmergencyContactName());
        existing.setEmergencyContactPhone(building.getEmergencyContactPhone());
        existing.setStatus(building.getStatus());
        if (building.getCustomer() != null && building.getCustomer().getId() != null) {
            Customer customer = customerRepository.findById(building.getCustomer().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
            existing.setCustomer(customer);
        }
        return buildingRepository.save(existing);
    }

    @Override
    public void deleteBuilding(Long id) {
        buildingRepository.deleteById(id);
    }

    @Override
    public Building getBuildingById(Long id) {
        return buildingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
    }

    @Override
    public List<Building> getBuildingsByCustomerId(Long customerId) {
        return buildingRepository.findByCustomerId(customerId);
    }

    @Override
    public List<Building> getAllBuildings() {
        return buildingRepository.findAll();
    }
}
