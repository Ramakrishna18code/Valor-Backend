package com.valor.reporting;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceReminderRulesTest {
    private final LocalDate today = LocalDate.of(2026,11,12);
    private final ServiceReminderService.Settings settings = new ServiceReminderService.Settings(3,7,15,false);

    @Test void overdueAndEmergencyTakePrecedenceOverDateWindows() {
        assertEquals("CRITICAL", ServiceReminderService.priority("ASSIGNED","MEDIUM",today.minusDays(1),today,settings));
        assertEquals("CRITICAL", ServiceReminderService.priority("PENDING","EMERGENCY",today.plusDays(7),today,settings));
        assertEquals("HIGH", ServiceReminderService.priority("ASSIGNED","MEDIUM",today,today,settings));
        assertEquals("MEDIUM", ServiceReminderService.priority("ASSIGNED","MEDIUM",today.plusDays(3),today,settings));
        assertEquals("NORMAL", ServiceReminderService.priority("ASSIGNED","MEDIUM",today.plusDays(4),today,settings));
        assertEquals("NORMAL", ServiceReminderService.priority("COMPLETED","EMERGENCY",today.minusDays(3),today,settings));
    }

    @Test void nonAmcWorkflowStagesFollowStoredQuotationAndAssignmentStates() {
        assertEquals("NEW_REQUEST", ServiceReminderService.stage("PENDING","MEDIUM",null,null,null,null));
        assertEquals("CUSTOMER_APPROVAL", ServiceReminderService.stage("PENDING","MEDIUM",null,null,"ISSUED",null));
        assertEquals("ASSIGNMENT_PENDING", ServiceReminderService.stage("PENDING","MEDIUM",null,null,"ACCEPTED",null));
        assertEquals("ACCEPTANCE_PENDING", ServiceReminderService.stage("ASSIGNED","MEDIUM",null,"ASSIGNED",null,null));
        assertEquals("ACCEPTANCE_PENDING", ServiceReminderService.stage("ASSIGNED","MEDIUM",today,"ASSIGNED",null,null));
        assertEquals("SCHEDULED", ServiceReminderService.stage("ASSIGNED","MEDIUM",today,"ACCEPTED",null,null));
        assertEquals("COMPLETION_PENDING", ServiceReminderService.stage("TESTING","MEDIUM",today,"ACCEPTED",null,null));
        assertEquals("PAYMENT_FOLLOW_UP", ServiceReminderService.stage("COMPLETED","MEDIUM",null,null,null,"ISSUED"));
        assertEquals("CANCELLED", ServiceReminderService.stage("CANCELLED","MEDIUM",null,null,null,null));
    }
}
