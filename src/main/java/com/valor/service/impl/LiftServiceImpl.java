package com.valor.service.impl;

import com.valor.entity.Building;
import com.valor.entity.Customer;
import com.valor.entity.Lift;
import com.valor.enums.AMCStatus;
import com.valor.enums.LiftStatus;
import com.valor.exception.ResourceNotFoundException;
import com.valor.repository.BuildingRepository;
import com.valor.repository.CustomerRepository;
import com.valor.repository.LiftRepository;
import com.valor.service.LiftService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LiftServiceImpl implements LiftService {

    private final LiftRepository liftRepository;
    private final CustomerRepository customerRepository;
    private final BuildingRepository buildingRepository;

    public LiftServiceImpl(LiftRepository liftRepository, CustomerRepository customerRepository, BuildingRepository buildingRepository) {
        this.liftRepository = liftRepository;
        this.customerRepository = customerRepository;
        this.buildingRepository = buildingRepository;
    }

    @Override
    public Lift createLift(Lift lift) {
        if (lift.getCustomer() == null || lift.getCustomer().getId() == null) {
            throw new IllegalArgumentException("Customer id is required");
        }
        Customer customer = customerRepository.findById(lift.getCustomer().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        lift.setCustomer(customer);
        if (lift.getBuilding() != null && lift.getBuilding().getId() != null) {
            Building building = buildingRepository.findById(lift.getBuilding().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
            if (building.getCustomer() != null && !building.getCustomer().getId().equals(customer.getId())) {
                throw new IllegalArgumentException("Building does not belong to the selected customer");
            }
            lift.setBuilding(building);
        }
        if (lift.getCurrentStatus() == null) lift.setCurrentStatus(LiftStatus.ACTIVE);
        if (lift.getAmcStatus() == null) lift.setAmcStatus(AMCStatus.NON_AMC);
        if (lift.getHealthScore() == null) lift.setHealthScore(100);
        return liftRepository.save(lift);
    }

    @Override
    public Lift updateLift(Long id, Lift lift) {
        Lift existing = liftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lift not found"));
        existing.setName(lift.getName());
        existing.setLiftNumber(lift.getLiftNumber());
        existing.setModel(lift.getModel());
        existing.setManufacturer(lift.getManufacturer());
        existing.setCapacity(lift.getCapacity());
        existing.setFloorCount(lift.getFloorCount());
        existing.setSerialNumber(lift.getSerialNumber());
        existing.setInstallationDate(lift.getInstallationDate());
        existing.setLocation(lift.getLocation());
        existing.setCurrentStatus(lift.getCurrentStatus() == null ? LiftStatus.ACTIVE : lift.getCurrentStatus());
        existing.setAmcStatus(lift.getAmcStatus() == null ? AMCStatus.NON_AMC : lift.getAmcStatus());
        existing.setWarrantyStatus(lift.getWarrantyStatus());
        existing.setWarrantyStartDate(lift.getWarrantyStartDate());
        existing.setWarrantyEndDate(lift.getWarrantyEndDate());
        existing.setLastMaintenanceDate(lift.getLastMaintenanceDate());
        existing.setNextMaintenanceDate(lift.getNextMaintenanceDate());
        existing.setTotalBreakdowns(lift.getTotalBreakdowns());
        existing.setHealthScore(lift.getHealthScore());
        existing.setMachineRoom(lift.getMachineRoom());
        existing.setQrCode(lift.getQrCode());
        existing.setSpecifications(lift.getSpecifications());
        if (lift.getCustomer() != null && lift.getCustomer().getId() != null) {
            Customer customer = customerRepository.findById(lift.getCustomer().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
            existing.setCustomer(customer);
        }
        if (lift.getBuilding() != null && lift.getBuilding().getId() != null) {
            Building building = buildingRepository.findById(lift.getBuilding().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
            existing.setBuilding(building);
        }
        return liftRepository.save(existing);
    }

    @Override
    public void deleteLift(Long id) {
        liftRepository.deleteById(id);
    }

    @Override
    public Lift getLiftById(Long id) {
        return liftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lift not found"));
    }

    @Override
    public List<Lift> getCustomerLifts(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return liftRepository.findByCustomer(customer);
    }

    @Override
    public List<Lift> searchLifts(String term) {
        return liftRepository.findByNameContainingIgnoreCase(term);
    }

    @Override
    public List<Lift> getBuildingLifts(Long buildingId) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new ResourceNotFoundException("Building not found");
        }
        return liftRepository.findByBuildingId(buildingId);
    }

    @Override
    public Page<Lift> searchLifts(String term, Pageable pageable) {
        return liftRepository.search(term, pageable);
    }
}
