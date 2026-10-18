package com.valor.installation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class InstallationMilestoneEligibilityService {
    private final ObjectMapper json;
    public InstallationMilestoneEligibilityService(ObjectMapper json){this.json=json;}

    /** Validation-only gate for future billing callers. It never creates invoices or changes state. */
    public void assertEligible(InstallationMilestone milestone, InstallationSchedule schedule, String trigger){
        if(milestone==null||schedule==null||milestone.getRequest()==null||schedule.getRequest()==null)throw blocked("Installation, milestone, and schedule are required");
        if(!milestone.getRequest().getId().equals(schedule.getRequest().getId()))throw blocked("Milestone and schedule belong to different installations");
        if(milestone.getSchedule()==null||milestone.getSchedule().getId()==null||!milestone.getSchedule().getId().equals(schedule.getId()))throw blocked("Milestone is not linked to the exact schedule version");
        if(schedule.getStatus()!=InstallationScheduleStatus.APPROVED)throw blocked("Schedule is not mutually approved");
        if(trigger==null||trigger.isBlank())throw blocked("Billing trigger is required");
        if(milestone.getStatus()==InstallationMilestoneStatus.CANCELLED||milestone.getStatus()==InstallationMilestoneStatus.PAID||milestone.getStatus()==InstallationMilestoneStatus.INVOICED)throw blocked("Milestone is not eligible");
        if(milestone.getMilestoneCode()==null||milestone.getMilestoneCode().isBlank())throw blocked("Milestone code is required");
        try {
            JsonNode definitions=json.readTree(schedule.getMilestoneDefinitions());
            if(!definitions.isArray()||definitions.isEmpty())throw blocked("Schedule has no milestone definitions");
            JsonNode found=null; for(JsonNode item:definitions)if(milestone.getMilestoneCode().equals(item.path("code").asText())){found=item;break;}
            if(found==null||!trigger.equals(found.path("trigger").asText())||found.path("trigger").asText().isBlank())throw blocked("Milestone trigger is not defined by the approved schedule");
            if(milestone.getAmount()==null||milestone.getAmount().compareTo(BigDecimal.ZERO)<=0)throw blocked("Milestone amount is not authoritative");
            if(milestone.getCurrency()==null||!milestone.getCurrency().equalsIgnoreCase(schedule.getCurrency()))throw blocked("Milestone currency does not match the approved schedule");
        } catch (InstallationEligibilityException e){throw e;} catch (Exception e){throw blocked("Schedule terms are incomplete or invalid");}
    }
    private static InstallationEligibilityException blocked(String message){return new InstallationEligibilityException(message);}
    public static class InstallationEligibilityException extends IllegalStateException { public InstallationEligibilityException(String message){super(message);} }
}
