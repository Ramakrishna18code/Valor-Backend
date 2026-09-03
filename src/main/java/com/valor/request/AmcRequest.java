package com.valor.request;

import com.valor.enums.AMCStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AmcRequest(
        @NotBlank(message = "Plan is required") String plan,
        String amcNumber,
        String coverageDetails,
        LocalDate startDate,
        LocalDate endDate,
        AMCStatus status,
        LocalDate renewalDate,
        LocalDate lastReminderSentAt,
        Integer renewalCount,
        @NotNull(message = "Lift id is required") Long liftId
) {
}