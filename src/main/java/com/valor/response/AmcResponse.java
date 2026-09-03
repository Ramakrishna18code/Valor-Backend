package com.valor.response;

import com.valor.enums.AMCStatus;

import java.time.LocalDate;

public record AmcResponse(
        Long id,
        String amcNumber,
        Long liftId,
        String plan,
        String coverageDetails,
        LocalDate startDate,
        LocalDate endDate,
        AMCStatus status,
        LocalDate renewalDate,
        LocalDate lastReminderSentAt,
        Integer renewalCount
) { }