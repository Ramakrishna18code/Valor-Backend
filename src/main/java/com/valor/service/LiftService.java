package com.valor.service;

import com.valor.entity.Lift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface LiftService {
    Lift createLift(Lift lift);
    Lift updateLift(Long id, Lift lift);
    void deleteLift(Long id);
    Lift getLiftById(Long id);
    List<Lift> getCustomerLifts(Long customerId);
    List<Lift> getBuildingLifts(Long buildingId);
    List<Lift> searchLifts(String term);
    Page<Lift> searchLifts(String term, Pageable pageable);
}
