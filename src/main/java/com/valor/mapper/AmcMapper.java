package com.valor.mapper;

import com.valor.entity.Amc;
import com.valor.entity.Lift;
import com.valor.request.AmcRequest;
import com.valor.response.AmcResponse;

public final class AmcMapper {

    private AmcMapper() {
    }

    public static Amc toEntity(AmcRequest request) {
        Amc amc = new Amc();
        amc.setPlan(request.plan());
        amc.setAmcNumber(request.amcNumber());
        amc.setCoverageDetails(request.coverageDetails());
        amc.setStartDate(request.startDate());
        amc.setEndDate(request.endDate());
        amc.setStatus(request.status());
        amc.setRenewalDate(request.renewalDate());
        amc.setLastReminderSentAt(request.lastReminderSentAt());
        amc.setRenewalCount(request.renewalCount());
        Lift lift = new Lift();
        lift.setId(request.liftId());
        amc.setLift(lift);
        return amc;
    }

    public static AmcResponse toResponse(Amc amc) {
        return new AmcResponse(
            amc.getId(),
            amc.getAmcNumber(),
            amc.getLift() == null ? null : amc.getLift().getId(),
            amc.getPlan(),
            amc.getCoverageDetails(),
            amc.getStartDate(),
            amc.getEndDate(),
            amc.getStatus(),
            amc.getRenewalDate(),
            amc.getLastReminderSentAt(),
            amc.getRenewalCount()
        );
    }
}