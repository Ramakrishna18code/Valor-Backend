package com.valor.installation;

import com.valor.auth.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.valor.installation.InstallationScheduleDtos.*;

@Service @Transactional
class InstallationScheduleService {
    private final InstallationScheduleRepository schedules;
    private final InstallationScheduleApprovalRepository approvals;
    private final InstallationRequestRepository requests;
    private final WorkflowIdentityAccess identities;
    private final AssetIdentityAccess access;
    private final AuditService audit;
    InstallationScheduleService(InstallationScheduleRepository s, InstallationScheduleApprovalRepository a, InstallationRequestRepository r, WorkflowIdentityAccess i, AssetIdentityAccess x, AuditService audit){schedules=s;approvals=a;requests=r;identities=i;access=x;this.audit=audit;}

    @Transactional(readOnly=true) List<View> list(Long requestId){access.requireAdmin();return schedules.findByRequestIdOrderByVersionNoDesc(requestId).stream().map(this::view).toList();}
    View create(Long requestId, Write in){access.requireAdmin();var r=request(requestId);validateTerms(in);var prior=schedules.findByRequestIdOrderByVersionNoDesc(requestId).stream().findFirst().orElse(null);var s=new InstallationSchedule();s.setRequest(r);s.setScheduleKey(prior==null?UUID.randomUUID().toString():prior.getScheduleKey());s.setVersionNo(prior==null?1:prior.getVersionNo()+1);s.setCurrency(in.currency().trim().toUpperCase());s.setMilestoneDefinitions(in.milestoneDefinitions().trim());s.setCreatedBy(access.actor());s.setStatus(InstallationScheduleStatus.DRAFT);var saved=schedules.saveAndFlush(s);audit.record("INSTALLATION_SCHEDULE_CREATE","INSTALLATION_SCHEDULE",saved.getId(),"Installation schedule version created");return view(saved);}
    View submit(Long id){access.requireAdmin();var s=schedule(id);validateTerms(new Write(s.getCurrency(),s.getMilestoneDefinitions()));if(s.getStatus()!=InstallationScheduleStatus.DRAFT)throw conflict("Only a draft schedule can be submitted");s.setStatus(InstallationScheduleStatus.AWAITING_APPROVAL);schedules.saveAndFlush(s);audit.record("INSTALLATION_SCHEDULE_SUBMIT","INSTALLATION_SCHEDULE",id,"Installation schedule submitted for approval");return view(s);}
    View companyApprove(Long id, Approval in){access.requireAdmin();return approve(id,InstallationScheduleApprovalType.COMPANY,access.actor(),in==null?null:in.reason());}
    View customerApprove(Long id, Approval in){var c=identities.customer(access.actor());var s=schedule(id);if(!s.getRequest().getCustomer().getId().equals(c.getId()))throw denied();return approve(id,InstallationScheduleApprovalType.CUSTOMER,access.actor(),in==null?null:in.reason());}
    private View approve(Long id,InstallationScheduleApprovalType type,User actor,String reason){var s=schedule(id);if(approvals.existsByScheduleIdAndApprovalType(id,type))return view(s);if(s.getStatus()!=InstallationScheduleStatus.AWAITING_APPROVAL)throw conflict("Schedule is not awaiting approval");var a=new InstallationScheduleApproval();a.setSchedule(s);a.setApprovalType(type);a.setActor(actor);a.setReason(clean(reason));approvals.saveAndFlush(a);if(approvals.existsByScheduleIdAndApprovalType(id,InstallationScheduleApprovalType.COMPANY)&&approvals.existsByScheduleIdAndApprovalType(id,InstallationScheduleApprovalType.CUSTOMER)){s.setStatus(InstallationScheduleStatus.APPROVED);s.setApprovedAt(java.time.LocalDateTime.now());schedules.saveAndFlush(s);audit.record("INSTALLATION_SCHEDULE_APPROVED","INSTALLATION_SCHEDULE",id,"Schedule mutually approved");}return view(s);}
    private void validateTerms(Write in){if(in==null||in.currency()==null||!in.currency().trim().matches("[A-Za-z]{3}")||in.milestoneDefinitions()==null||in.milestoneDefinitions().trim().isBlank())throw new IllegalArgumentException("Complete schedule terms are required");}
    private InstallationRequest request(Long id){return requests.findById(id).orElseThrow(()->new IllegalArgumentException("Installation not found"));}
    private InstallationSchedule schedule(Long id){return schedules.findById(id).orElseThrow(()->new IllegalArgumentException("Schedule not found"));}
    private View view(InstallationSchedule s){return new View(s.getId(),s.getRequest().getId(),s.getScheduleKey(),s.getVersionNo(),s.getStatus(),s.getCurrency(),s.getMilestoneDefinitions(),approvals.existsByScheduleIdAndApprovalType(s.getId(),InstallationScheduleApprovalType.COMPANY),approvals.existsByScheduleIdAndApprovalType(s.getId(),InstallationScheduleApprovalType.CUSTOMER),s.getCreatedAt(),s.getApprovedAt());}
    private static String clean(String s){return s==null||s.isBlank()?null:s.trim();} private static IllegalStateException conflict(String s){return new IllegalStateException(s);} private static AccessDeniedException denied(){return new AccessDeniedException("Access denied");}
}
