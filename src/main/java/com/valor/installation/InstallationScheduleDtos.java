package com.valor.installation;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

final class InstallationScheduleDtos {
    record Write(@NotBlank @Size(max=3) String currency, @NotBlank @Size(max=10000) String milestoneDefinitions) {}
    record Approval(@Size(max=500) String reason) {}
    record View(Long id, Long installationRequestId, String scheduleKey, Integer versionNo, InstallationScheduleStatus status, String currency, String milestoneDefinitions, boolean companyApproved, boolean customerApproved, LocalDateTime createdAt, LocalDateTime approvedAt) {}
}
