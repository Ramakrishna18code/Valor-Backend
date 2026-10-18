package com.valor.installation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

class InstallationMilestoneEligibilityTest {
    private final InstallationMilestoneEligibilityService gate = new InstallationMilestoneEligibilityService(new ObjectMapper());

    @Test
    void incompleteOrMissingTriggerIsBlocked() {
        var request = request(7L);
        var schedule = schedule(21L, request, InstallationScheduleStatus.APPROVED, "[{\"code\":\"ADVANCE\",\"trigger\":\"SITE_READY\",\"amount\":100}]");
        var milestone = milestone(request, schedule, "ADVANCE");
        assertThrows(InstallationMilestoneEligibilityService.InstallationEligibilityException.class, () -> gate.assertEligible(milestone, schedule, ""));
        assertThrows(InstallationMilestoneEligibilityService.InstallationEligibilityException.class, () -> gate.assertEligible(milestone, schedule, "CUSTOMER_ACCEPTED"));
    }

    @Test
    void exactApprovedScheduleAndExplicitTermsPass() {
        var request = request(7L);
        var schedule = schedule(21L, request, InstallationScheduleStatus.APPROVED, "[{\"code\":\"ADVANCE\",\"trigger\":\"SITE_READY\",\"amount\":100}]");
        var milestone = milestone(request, schedule, "ADVANCE");
        gate.assertEligible(milestone, schedule, "SITE_READY");
    }

    @Test
    void mismatchedInstallationOrCancelledMilestoneIsBlocked() {
        var request = request(7L);
        var other = request(8L);
        var schedule = schedule(21L, request, InstallationScheduleStatus.APPROVED, "[{\"code\":\"ADVANCE\",\"trigger\":\"SITE_READY\"}]");
        var milestone = milestone(other, schedule, "ADVANCE");
        assertThrows(InstallationMilestoneEligibilityService.InstallationEligibilityException.class, () -> gate.assertEligible(milestone, schedule, "SITE_READY"));
        milestone.setRequest(request); milestone.setStatus(InstallationMilestoneStatus.CANCELLED);
        assertThrows(InstallationMilestoneEligibilityService.InstallationEligibilityException.class, () -> gate.assertEligible(milestone, schedule, "SITE_READY"));
    }

    private InstallationRequest request(Long id){var r=org.mockito.Mockito.mock(InstallationRequest.class);when(r.getId()).thenReturn(id);return r;}
    private InstallationSchedule schedule(Long id, InstallationRequest request, InstallationScheduleStatus status, String definitions){var s=new InstallationSchedule();s.setId(id);s.setRequest(request);s.setStatus(status);s.setCurrency("INR");s.setMilestoneDefinitions(definitions);return s;}
    private InstallationMilestone milestone(InstallationRequest request, InstallationSchedule schedule, String code){var m=new InstallationMilestone();m.setRequest(request);m.setSchedule(schedule);m.setMilestoneCode(code);m.setStatus(InstallationMilestoneStatus.PLANNED);m.setAmount(new BigDecimal("100.00"));m.setCurrency("INR");return m;}
}
