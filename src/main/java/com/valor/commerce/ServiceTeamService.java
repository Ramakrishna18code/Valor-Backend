package com.valor.commerce;

import com.valor.auth.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class ServiceTeamService {
    record HelperRequest(long helperTechnicianProfileId,String reason) {}
    record Review(boolean approve,String reason,String customerDisplayName) {}
    record WorkLog(LocalDate workDate,int minutesWorked,String description) {}
    record LogReview(boolean approve) {}
    record Allowance(String basis,int units,BigDecimal rate,String reason) {}
    record Transfer(String reference,Long evidenceId) {Transfer(String reference){this(reference,null);}}
    private final FinanceAccess a;
    ServiceTeamService(FinanceAccess a){this.a=a;}
    record PublicName(String name) {}
    Map<String,Object> publicName(long technicianId,PublicName input){a.admin();if(input==null)throw new CommerceException(400,"Enter the technician name shown to customers");String name=FinanceAccess.text(input.name(),160);a.one("SELECT id FROM technician_profiles WHERE id=?",technicianId);a.db.update("UPDATE technician_profiles SET public_name=? WHERE id=?",name,technicianId);return a.one("SELECT id,public_name FROM technician_profiles WHERE id=?",technicianId);}
    @Transactional(readOnly=true)
    List<Map<String,Object>> candidates(){long current=a.technician();return a.rows("SELECT t.id,t.employee_id FROM technician_profiles t JOIN users u ON u.id=t.user_id WHERE t.is_active=TRUE AND u.is_active=TRUE AND t.id<>? ORDER BY t.employee_id,t.id LIMIT 100",current);}
    @Transactional(readOnly=true)
    List<Map<String,Object>> helperAssignments(){long helper=a.technician();return a.rows("SELECT m.id AS team_member_id,r.id AS service_request_id,r.title,r.status,b.building_name,l.name AS lift_name,m.customer_display_name FROM service_team_members m JOIN service_requests r ON r.id=m.service_request_id LEFT JOIN lifts l ON l.id=r.lift_id LEFT JOIN buildings b ON b.id=l.building_id WHERE m.helper_technician_profile_id=? AND m.status='APPROVED' ORDER BY m.id DESC",helper);}
    @Transactional(readOnly=true)
    Map<String,Object> view(long job){
        var request=a.one("SELECT r.id,c.user_id AS customer_user_id FROM service_requests r JOIN customer_profiles c ON c.id=r.customer_id WHERE r.id=?",job);
        Role role=a.actor().getRole();
        if(role==Role.CUSTOMER&&((Number)request.get("customerUserId")).longValue()!=a.actor().getId())throw new AccessDeniedException("Service request access required");
        if(role==Role.TECHNICIAN&&a.count("SELECT COUNT(*) FROM technician_assignments WHERE service_request_id=? AND technician_id=? AND status IN ('ASSIGNED','ACCEPTED','COMPLETED')",job,a.technician())==0&&a.count("SELECT COUNT(*) FROM service_team_members WHERE service_request_id=? AND helper_technician_profile_id=? AND status='APPROVED'",job,a.technician())==0)throw new AccessDeniedException("Team access required");
        if(!List.of(Role.CUSTOMER,Role.TECHNICIAN,Role.ADMIN,Role.SUPER_ADMIN).contains(role))throw new AccessDeniedException("Team access required");
        var result=new LinkedHashMap<String,Object>();
        result.put("lead",a.rows("SELECT 'Lead technician' AS role,COALESCE(t.public_name,'Lead technician') AS display_name,ta.status"+(role==Role.CUSTOMER?"":",t.id AS technician_profile_id")+" FROM technician_assignments ta JOIN technician_profiles t ON t.id=ta.technician_id WHERE ta.service_request_id=? AND ta.status IN ('ASSIGNED','ACCEPTED','COMPLETED') ORDER BY ta.id DESC LIMIT 1",job));
        result.put("helpers",a.rows("SELECT id,'Helper technician' AS role,customer_display_name AS display_name,status FROM service_team_members WHERE service_request_id=? AND status='APPROVED' ORDER BY id",job));
        if(role!=Role.CUSTOMER){result.put("requests",a.rows("SELECT id,helper_technician_profile_id,status,request_reason,decision_reason,requested_at,decided_at FROM service_team_members WHERE service_request_id=? ORDER BY id",job));result.put("workLogs",a.rows("SELECT w.id,w.team_member_id,w.work_date,w.minutes_worked,w.description,w.status,w.created_at FROM service_helper_work_logs w JOIN service_team_members m ON m.id=w.team_member_id WHERE m.service_request_id=? ORDER BY w.id",job));}
        if(role==Role.ADMIN||role==Role.SUPER_ADMIN)result.put("allowances",a.rows("SELECT x.* FROM service_helper_allowances x JOIN service_team_members m ON m.id=x.team_member_id WHERE m.service_request_id=? ORDER BY x.id",job));
        return result;
    }
    Map<String,Object> request(long job,HelperRequest input){
        long lead=a.technician();a.job(job,true);a.one("SELECT id FROM service_requests WHERE id=? FOR UPDATE",job);
        if(input==null||input.helperTechnicianProfileId()==lead)throw new CommerceException(400,"Choose another technician to help");
        if(a.count("SELECT COUNT(*) FROM technician_assignments WHERE service_request_id=? AND technician_id=? AND status='ACCEPTED'",job,lead)==0)throw new AccessDeniedException("Only the lead technician can request a helper");
        a.one("SELECT id FROM technician_profiles WHERE id=? AND is_active=TRUE",input.helperTechnicianProfileId());
        if(a.count("SELECT COUNT(*) FROM service_team_members WHERE service_request_id=? AND helper_technician_profile_id=?",job,input.helperTechnicianProfileId())>0)throw new CommerceException(409,"This helper has already been requested");
        long id=a.insert("INSERT INTO service_team_members(service_request_id,lead_technician_profile_id,helper_technician_profile_id,request_reason,requested_by_user_id) VALUES(?,?,?,?,?)",job,lead,input.helperTechnicianProfileId(),FinanceAccess.text(input.reason(),1000),a.actor().getId());
        return a.one("SELECT id,status,request_reason,requested_at FROM service_team_members WHERE id=?",id);
    }
    Map<String,Object> review(long member,Review input){
        a.admin();var row=a.one("SELECT * FROM service_team_members WHERE id=? FOR UPDATE",member);long job=((Number)row.get("serviceRequestId")).longValue();a.one("SELECT id FROM service_requests WHERE id=? FOR UPDATE",job);
        if(input==null)throw new CommerceException(400,"Choose whether to approve this helper");
        if(!"REQUESTED".equals(row.get("status")))throw new CommerceException(409,"This helper request has already been reviewed");
        if(input.approve()&&a.count("SELECT COUNT(*) FROM service_team_members WHERE service_request_id=? AND status='APPROVED'",job)>=3)throw new CommerceException(409,"A service team can have up to three helpers");
        String name=input.customerDisplayName()==null?null:FinanceAccess.text(input.customerDisplayName(),160);
        if(input.approve()&&name==null)throw new CommerceException(400,"Enter the helper name shown to the customer");
        a.db.update("UPDATE service_team_members SET status=?,decision_reason=?,customer_display_name=?,approved_by_user_id=?,decided_at=CURRENT_TIMESTAMP WHERE id=?",input.approve()?"APPROVED":"REJECTED",FinanceAccess.text(input.reason(),1000),name,a.actor().getId(),member);
        return a.one("SELECT id,status,customer_display_name,decided_at FROM service_team_members WHERE id=?",member);
    }
    Map<String,Object> log(long member,WorkLog input){
        long tech=a.technician();var row=a.one("SELECT m.*,r.status AS job_status FROM service_team_members m JOIN service_requests r ON r.id=m.service_request_id WHERE m.id=?",member);
        if(((Number)row.get("helperTechnicianProfileId")).longValue()!=tech||!"APPROVED".equals(row.get("status")))throw new AccessDeniedException("Approved helper access required");
        if(List.of("COMPLETED","CANCELLED").contains(row.get("jobStatus")))throw new CommerceException(409,"This service request is closed");
        if(input==null||input.workDate()==null||input.minutesWorked()<1||input.minutesWorked()>1440||input.workDate().isAfter(LocalDate.now()))throw new CommerceException(400,"Enter a valid work date and duration");
        long id=a.insert("INSERT INTO service_helper_work_logs(team_member_id,work_date,minutes_worked,description,submitted_by_user_id) VALUES(?,?,?,?,?)",member,input.workDate(),input.minutesWorked(),FinanceAccess.text(input.description(),1000),a.actor().getId());
        return a.one("SELECT id,work_date,minutes_worked,description,status,created_at FROM service_helper_work_logs WHERE id=?",id);
    }
    Map<String,Object> reviewLog(long id,LogReview input){a.admin();if(input==null)throw new CommerceException(400,"Choose whether to approve this work log");var row=a.one("SELECT * FROM service_helper_work_logs WHERE id=? FOR UPDATE",id);if(!"SUBMITTED".equals(row.get("status")))throw new CommerceException(409,"This work log has already been reviewed");a.db.update("UPDATE service_helper_work_logs SET status=?,reviewed_by_user_id=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=?",input.approve()?"APPROVED":"REJECTED",a.actor().getId(),id);return a.one("SELECT id,status,reviewed_at FROM service_helper_work_logs WHERE id=?",id);}
    Map<String,Object> allowance(long member,Allowance input){
        a.admin();var team=a.one("SELECT * FROM service_team_members WHERE id=? FOR UPDATE",member);
        if(!"APPROVED".equals(team.get("status")))throw new CommerceException(409,"Approve the helper before recording an allowance");
        if(input==null||!FinanceAccess.oneOf(input.basis(),"JOB","DAY")||input.units()<1||"JOB".equals(input.basis())&&input.units()!=1)throw new CommerceException(400,"Choose a per-job or per-day allowance and valid units");
        BigDecimal rate=FinanceAccess.money(input.rate(),false),amount=rate.multiply(BigDecimal.valueOf(input.units()));FinanceAccess.money(amount,false);
        int days=a.db.queryForObject("SELECT COUNT(DISTINCT work_date) FROM service_helper_work_logs WHERE team_member_id=? AND status='APPROVED'",Integer.class,member);
        if(days==0||"DAY".equals(input.basis())&&input.units()>days)throw new CommerceException(409,"Approve the helper work logs before setting the allowance");
        if(a.count("SELECT COUNT(*) FROM service_helper_allowances WHERE team_member_id=?",member)>0)throw new CommerceException(409,"An allowance has already been recorded for this helper");
        long id=a.insert("INSERT INTO service_helper_allowances(team_member_id,basis,units,rate_snapshot,amount,reason) VALUES(?,?,?,?,?,?)",member,input.basis(),input.units(),rate,amount,FinanceAccess.text(input.reason(),1000));return a.one("SELECT * FROM service_helper_allowances WHERE id=?",id);
    }
    Map<String,Object> approveAllowance(long id){
        a.admin();var row=a.one("SELECT x.*,m.service_request_id FROM service_helper_allowances x JOIN service_team_members m ON m.id=x.team_member_id WHERE x.id=? FOR UPDATE",id);
        if(!"DRAFT".equals(row.get("status")))throw new CommerceException(409,"This allowance has already been reviewed");
        if(a.count("SELECT COUNT(*) FROM service_requests WHERE id=? AND status='COMPLETED'",row.get("serviceRequestId"))==0)throw new CommerceException(409,"Complete the service before approving helper payment");
        a.db.update("UPDATE service_helper_allowances SET status='APPROVED',approved_by_user_id=?,approved_at=CURRENT_TIMESTAMP WHERE id=?",a.actor().getId(),id);return a.one("SELECT * FROM service_helper_allowances WHERE id=?",id);
    }
    private void requireVerifiedCollection(long job){
        var invoices=a.rows("SELECT id,status FROM invoices WHERE service_request_id=? AND status NOT IN ('VOID','CANCELLED')",job);
        if(!invoices.isEmpty()){
            for(var invoice:invoices)if(!"PAID".equals(invoice.get("status"))||a.count("SELECT COUNT(*) FROM payment_records WHERE invoice_id=? AND status='SUCCEEDED'",invoice.get("id"))==0)
                throw new CommerceException(409,"Confirm every customer invoice payment before paying this helper");
            return;
        }
        var decisions=a.rows("SELECT contract_id,outcome FROM service_request_coverage_decisions WHERE service_request_id=? AND scope='VISIT' ORDER BY version_number DESC LIMIT 1",job);
        if(decisions.isEmpty()||!"COVERED".equals(decisions.get(0).get("outcome"))||decisions.get(0).get("contractId")==null)
            throw new CommerceException(409,"Confirm customer collection or a covered maintenance visit before paying this helper");
        long contract=((Number)decisions.get(0).get("contractId")).longValue();
        if(a.count("SELECT COUNT(*) FROM payment_records WHERE amc_contract_id=? AND status='SUCCEEDED'",contract)==0)
            throw new CommerceException(409,"Confirm the maintenance plan payment before paying this helper");
        if(a.count("SELECT COUNT(*) FROM payment_refunds f JOIN payment_records p ON p.id=f.payment_id WHERE p.amc_contract_id=? AND f.status IN ('REQUESTED','PROCESSING','SUCCEEDED')",contract)>0)
            throw new CommerceException(409,"Review the maintenance plan refund before paying this helper");
    }
    Map<String,Object> payAllowance(long id,Transfer input){
        a.admin();if(input==null||input.evidenceId()==null)throw new CommerceException(400,"Upload bank transfer evidence before recording helper payment");
        var row=a.one("SELECT x.*,m.service_request_id FROM service_helper_allowances x JOIN service_team_members m ON m.id=x.team_member_id WHERE x.id=? FOR UPDATE",id);
        if(!"APPROVED".equals(row.get("status")))throw new CommerceException(409,"Approve the allowance before recording payment");
        if(((Number)row.get("approvedByUserId")).longValue()==a.actor().getId())throw new CommerceException(409,"A second administrator must confirm helper payment");
        long job=((Number)row.get("serviceRequestId")).longValue();
        if(a.count("SELECT COUNT(*) FROM service_requests WHERE id=? AND status='COMPLETED'",job)==0)throw new CommerceException(409,"Complete the service before paying this helper");
        requireVerifiedCollection(job);
        if(a.count("SELECT COUNT(*) FROM payment_refunds f JOIN payment_records p ON p.id=f.payment_id WHERE p.service_request_id=? AND f.status IN ('REQUESTED','PROCESSING','SUCCEEDED')",job)>0)throw new CommerceException(409,"Review the refund and allowance before paying this helper");
        if(a.count("SELECT COUNT(*) FROM finance_evidence WHERE id=? AND helper_allowance_id=? AND uploaded_by_user_id=?",input.evidenceId(),id,a.actor().getId())==0)
            throw new CommerceException(400,"Upload bank confirmation evidence for this helper allowance");
        String reference=FinanceAccess.text(input.reference(),120);
        a.db.update("UPDATE service_helper_allowances SET status='PAID',transfer_reference=?,evidence_attachment_id=?,paid_by_user_id=?,paid_at=CURRENT_TIMESTAMP WHERE id=?",reference,input.evidenceId(),a.actor().getId(),id);
        return a.one("SELECT * FROM service_helper_allowances WHERE id=?",id);
    }
}
