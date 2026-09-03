package com.valor.service;

import com.valor.entity.Building;

import java.util.List;

public interface BuildingService {
    Building createBuilding(Building building);
    Building updateBuilding(Long id, Building building);
    void deleteBuilding(Long id);
    Building getBuildingById(Long id);
    List<Building> getBuildingsByCustomerId(Long customerId);
    List<Building> getAllBuildings();
}
