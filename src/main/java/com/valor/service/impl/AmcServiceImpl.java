package com.valor.service.impl;

import com.valor.entity.Amc;
import com.valor.entity.Lift;
import com.valor.enums.AMCStatus;
import com.valor.exception.ResourceNotFoundException;
import com.valor.repository.AmcRepository;
import com.valor.repository.LiftRepository;
import com.valor.service.AmcService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AmcServiceImpl implements AmcService {

    private final AmcRepository amcRepository;
    private final LiftRepository liftRepository;

    public AmcServiceImpl(AmcRepository amcRepository, LiftRepository liftRepository) {
        this.amcRepository = amcRepository;
        this.liftRepository = liftRepository;
    }

    @Override
    public Amc createAmc(Amc amc) {
        if (amc.getLift() == null || amc.getLift().getId() == null) {
            throw new IllegalArgumentException("Lift id is required");
        }
        Lift lift = liftRepository.findById(amc.getLift().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Lift not found"));
        amc.setLift(lift);
        if (amc.getAmcNumber() == null || amc.getAmcNumber().isBlank()) {
            amc.setAmcNumber("VAL-AMC-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        }
        if (amc.getStatus() == null) amc.setStatus(AMCStatus.ACTIVE);
        if (amc.getRenewalCount() == null) amc.setRenewalCount(0);
        return amcRepository.save(amc);
    }

    @Override
    public Amc renewAmc(Long id, Amc amc) {
        Amc existing = amcRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AMC not found"));
        existing.setAmcNumber(amc.getAmcNumber());
        existing.setPlan(amc.getPlan());
        existing.setCoverageDetails(amc.getCoverageDetails());
        existing.setEndDate(amc.getEndDate());
        existing.setStatus(AMCStatus.RENEWED);
        existing.setRenewalDate(amc.getRenewalDate());
        existing.setLastReminderSentAt(amc.getLastReminderSentAt());
        existing.setRenewalCount(amc.getRenewalCount());
        if (amc.getLift() != null && amc.getLift().getId() != null) {
            existing.setLift(liftRepository.findById(amc.getLift().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lift not found")));
        }
        return amcRepository.save(existing);
    }

    @Override
    public void cancelAmc(Long id) {
        amcRepository.deleteById(id);
    }

    @Override
    public Amc getAmcById(Long id) {
        return amcRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AMC not found"));
    }

    @Override
    public List<Amc> getAllAmcs() {
        return amcRepository.findAll();
    }

    @Override
    public Page<Amc> searchAmcs(String term, Pageable pageable) {
        return amcRepository.search(term, pageable);
    }
}
