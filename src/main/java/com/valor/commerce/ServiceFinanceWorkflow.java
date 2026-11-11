package com.valor.commerce;

import com.valor.auth.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class ServiceFinanceWorkflow {
    record Inspection(String reportedProblem,String observedProblem,String rootCause,String severity,String safetyStatus,
                      String recommendedRepair,String requiredLabour,Integer estimatedDurationMinutes,String technicianRemarks,List<Long> evidenceIds,String requiredSpareParts,boolean draft) {
        Inspection(String reported,String observed,String cause,String severity,String safety,String repair,String labour,Integer minutes,String remarks,List<Long> evidence){this(reported,observed,cause,severity,safety,repair,labour,minutes,remarks,evidence,null,false);}
    }
    record Review(boolean approve,boolean clearSafety,String reason) {}
    record Work(String description,String reason) {}
    record WorkReview(Long quotationId,boolean reject,String reason) {}
    record Usage(long lineId,String action,int quantity,String reason,String idempotencyKey) {}
    record Purchase(long partItemId,Long serviceRequestId,String supplierName,String reference,int quantity,BigDecimal unitCost) {}
    record Hold(String reason,Long resolveId) {}
    record CompletionTerms(String warrantyDetails,String handoverNotes) {}
    final FinanceAccess a;
    ServiceFinanceWorkflow(FinanceAccess a){this.a=a;}

    @Transactional(readOnly=true)
    Map<String,Object> view(long job) {
        var jobDetails=a.job(job,false);
        var result=new LinkedHashMap<String,Object>();
        BigDecimal total=a.sum("SELECT COALESCE(SUM(total_amount),0) FROM invoices WHERE service_request_id=? AND status IN ('ISSUED','PAID') AND currency='INR'",job);
        BigDecimal paid=a.sum("SELECT COALESCE(SUM(amount),0) FROM payment_records WHERE service_request_id=? AND invoice_id IS NOT NULL AND status IN ('SUCCEEDED','PARTIALLY_REFUNDED','REFUNDED') AND currency='INR'",job);
        BigDecimal refunded=a.sum("SELECT COALESCE(SUM(f.amount),0) FROM payment_refunds f JOIN payment_records p ON p.id=f.payment_id WHERE p.service_request_id=? AND p.invoice_id IS NOT NULL AND f.status='SUCCEEDED' AND f.currency='INR'",job);
        BigDecimal issued=a.sum("SELECT COALESCE(SUM(total_amount),0) FROM invoices WHERE service_request_id=? AND status='ISSUED' AND currency='INR'",job);
        BigDecimal paidOnIssued=a.sum("SELECT COALESCE(SUM(p.amount),0) FROM payment_records p JOIN invoices i ON i.id=p.invoice_id WHERE i.service_request_id=? AND i.status='ISSUED' AND i.currency='INR' AND p.currency='INR' AND p.status IN ('SUCCEEDED','PARTIALLY_REFUNDED','REFUNDED')",job);
        BigDecimal refundedOnIssued=a.sum("SELECT COALESCE(SUM(f.amount),0) FROM payment_refunds f JOIN payment_records p ON p.id=f.payment_id JOIN invoices i ON i.id=p.invoice_id WHERE i.service_request_id=? AND i.status='ISSUED' AND i.currency='INR' AND f.currency='INR' AND f.status='SUCCEEDED'",job);
        BigDecimal due=issued.subtract(paidOnIssued).add(refundedOnIssued).max(BigDecimal.ZERO);
        result.put("billingSummary",Map.of("totalInvoiced",total,"confirmedPaid",paid,"refunded",refunded,"netReceived",paid.subtract(refunded),"amountDue",due,"currency","INR"));
        result.put("quotationAvailable",a.count("SELECT COUNT(*) FROM service_quotations WHERE service_request_id=? AND release_status='RELEASED' AND status IN ('ISSUED','ACCEPTED')",job)>0);
        result.put("completionTerms",a.rows("SELECT version_number,warranty_details,handover_notes,created_at FROM service_completion_terms WHERE service_request_id=? ORDER BY version_number DESC LIMIT 1",job));
        var inspections=a.rows("SELECT i.*,t.employee_id AS technician_name FROM service_inspections i JOIN technician_profiles t ON t.id=i.technician_profile_id WHERE i.service_request_id=?"+(a.actor().getRole()==Role.CUSTOMER?" AND i.status<>'DRAFT'":"")+" ORDER BY i.id DESC",job);
        for(var inspection:inspections)inspection.put("evidence",a.rows("SELECT att.id,att.original_filename,att.content_type FROM service_inspection_evidence e JOIN service_request_attachments att ON att.id=e.attachment_id WHERE e.inspection_id=?",inspection.get("id")));
        result.put("inspections",inspections);
        result.put("workApprovals",a.rows("SELECT w.*,q.status AS quotation_status,q.quotation_number FROM service_work_approvals w LEFT JOIN service_quotations q ON q.id=w.quotation_id WHERE w.service_request_id=? ORDER BY w.id DESC",job));
        result.put("milestones",a.rows("SELECT m.*,q.status AS quotation_status,i.status AS invoice_status,i.invoice_number,COALESCE((SELECT SUM(p.amount) FROM payment_records p WHERE p.invoice_id=i.id AND p.status IN ('SUCCEEDED','PARTIALLY_REFUNDED','REFUNDED')),0) AS paid_amount,COALESCE((SELECT SUM(f.amount) FROM payment_refunds f JOIN payment_records p ON p.id=f.payment_id WHERE p.invoice_id=i.id AND f.status='SUCCEEDED'),0) AS refunded_amount FROM service_payment_milestones m JOIN service_quotations q ON q.id=m.quotation_id LEFT JOIN invoices i ON i.id=m.invoice_id WHERE m.service_request_id=? AND q.release_status='RELEASED' ORDER BY m.quotation_id,m.sequence_number",job));
        @SuppressWarnings("unchecked") var paymentStages=(List<Map<String,Object>>)result.get("milestones");
        for(var stage:paymentStages){stage.put("jobStatus",jobDetails.get("status"));var latest=a.rows("SELECT id,status,COALESCE(razorpay_payment_id,provider_reference) AS provider_reference FROM payment_records WHERE invoice_id=? ORDER BY id DESC LIMIT 1",stage.get("invoiceId"));String paymentStatus=latest.isEmpty()?"":String.valueOf(latest.get(0).get("status"));String state=stage.get("invoiceId")==null?"AWAITING_APPROVAL":((BigDecimal)stage.get("refundedAmount")).signum()>0?"REFUNDED":"PAID".equals(stage.get("invoiceStatus"))?"PAID":List.of("PENDING","PROCESSING").contains(paymentStatus)?"PENDING_VERIFICATION":!FinanceAccess.stageDue(stage)?"UPCOMING":"FAILED".equals(paymentStatus)?"FAILED":"DUE";stage.put("paymentStatus",state);stage.put("paymentEligible",List.of("DUE","FAILED").contains(state));if(!latest.isEmpty()){stage.put("paymentId",latest.get(0).get("id"));stage.put("paymentReference",latest.get(0).get("providerReference"));}stage.remove("jobStatus");}
        result.put("parts",a.rows("SELECT l.id,l.part_item_id,p.name,l.approved_quantity,l.issued_quantity,r.status,COALESCE((SELECT SUM(u.quantity) FROM job_part_usage u WHERE u.part_line_id=l.id AND u.action='RECEIVED'),0) AS received_quantity,COALESCE((SELECT SUM(u.quantity) FROM job_part_usage u WHERE u.part_line_id=l.id AND u.action='INSTALLED'),0) AS installed_quantity,COALESCE((SELECT SUM(u.quantity) FROM job_part_usage u WHERE u.part_line_id=l.id AND u.action='RETURNED'),0) AS returned_quantity FROM service_request_part_lines l JOIN service_request_part_requests r ON r.id=l.request_id JOIN part_items p ON p.id=l.part_item_id WHERE r.service_request_id=? ORDER BY l.id",job));
        if(a.actor().getRole()!=Role.CUSTOMER){@SuppressWarnings("unchecked") var stages=(List<Map<String,Object>>)result.get("milestones");for(var stage:stages){var cash=a.rows("SELECT o.id AS otp_id,p.id AS payment_id,o.expires_at FROM cash_payment_otps o JOIN payment_records p ON p.id=o.payment_id WHERE p.invoice_id=? AND p.status='PROCESSING' AND p.provider_reference='CASH' AND o.verified_at IS NULL ORDER BY o.id DESC LIMIT 1",stage.get("invoiceId"));if(!cash.isEmpty())stage.put("cashCollection",cash.get(0));}}
        result.put("holds",a.rows("SELECT id,reason,resolved_at,created_at FROM service_finance_holds WHERE service_request_id=? ORDER BY id DESC",job));
        result.put("amcCoverage",a.rows("SELECT c.id,c.amc_number,c.plan,c.coverage_details,c.start_date,c.end_date,c.status FROM amc_contracts c JOIN service_request_coverage_decisions d ON d.contract_id=c.id WHERE d.service_request_id=? AND d.scope='VISIT' AND d.version_number=(SELECT MAX(v.version_number) FROM service_request_coverage_decisions v WHERE v.service_request_id=d.service_request_id AND v.scope=d.scope) ORDER BY c.id DESC",job));
        result.put("coverageDecisions",a.rows("SELECT service_type,scope,service_date,outcome,reason,decided_at FROM service_request_coverage_decisions d WHERE d.service_request_id=? AND d.version_number=(SELECT MAX(v.version_number) FROM service_request_coverage_decisions v WHERE v.service_request_id=d.service_request_id AND v.scope=d.scope) ORDER BY d.id",job));
        result.put("customerTimeline",customerTimeline(job));
        result.put("events",a.actor().getRole()==Role.CUSTOMER?List.of():a.rows("SELECT event_type,reason,created_at FROM service_finance_events WHERE service_request_id=?"+(a.actor().getRole()==Role.TECHNICIAN?" AND event_type NOT LIKE 'COMMISSION%' AND event_type NOT LIKE 'EXPENSE%'":"")+" ORDER BY id DESC LIMIT 100",job));
        return result;
    }
    private List<Map<String,Object>> customerTimeline(long job) {
        var events=new ArrayList<Map<String,Object>>();
        for(var row:a.rows("SELECT to_status,changed_at FROM service_status_history WHERE service_request_id=? ORDER BY id",job)) {
            String status=String.valueOf(row.get("toStatus"));
            String title=switch(status) {
                case "PENDING" -> "Request submitted"; case "ASSIGNED" -> "Technician assigned";
                case "ACCEPTED" -> "Technician accepted the job"; case "ON_THE_WAY" -> "Technician on the way";
                case "REACHED_SITE" -> "Technician reached the site"; case "DIAGNOSIS" -> "Inspection in progress";
                case "REPAIR_IN_PROGRESS" -> "Approved work in progress"; case "WAITING_FOR_PARTS" -> "Waiting for parts";
                case "TESTING" -> "Lift testing in progress"; case "COMPLETED" -> "Service completed";
                case "CANCELLED" -> "Request cancelled"; default -> null;
            };
            addTimeline(events,"REQUEST",title,row.get("changedAt"),null);
        }
        for(var row:a.rows("SELECT event_type,scheduled_date,start_time,changed_at FROM service_visit_history WHERE service_request_id=? ORDER BY id",job)) {
            String title=switch(String.valueOf(row.get("eventType"))) {
                case "CREATED", "SCHEDULED" -> "Visit scheduled"; case "RESCHEDULED", "TECHNICIAN_CHANGED" -> "Visit updated";
                case "CANCELLED" -> "Visit cancelled"; case "COMPLETED" -> "Visit completed"; default -> null;
            };
            String detail=row.get("scheduledDate")==null?null:String.valueOf(row.get("scheduledDate"))+(row.get("startTime")==null?"":" at "+row.get("startTime"));
            addTimeline(events,"VISIT",title,row.get("changedAt"),detail);
        }
        for(var row:a.rows("SELECT status,reviewed_at FROM service_inspections WHERE service_request_id=? AND status<>'DRAFT' ORDER BY id",job)) {
            if(row.get("reviewedAt")!=null)addTimeline(events,"INSPECTION","APPROVED".equals(row.get("status"))?"Inspection reviewed":"Inspection needs more review",row.get("reviewedAt"),null);
        }
        for(var row:a.rows("SELECT created_at FROM service_finance_events WHERE service_request_id=? AND event_type='INSPECTION_SUBMITTED' ORDER BY id",job))
            addTimeline(events,"INSPECTION","Inspection submitted",row.get("createdAt"),null);
        for(var row:a.rows("SELECT version_number,status,released_at,responded_at FROM service_quotations WHERE service_request_id=? AND release_status='RELEASED' ORDER BY id",job)) {
            String detail="Version "+row.get("versionNumber");
            addTimeline(events,"QUOTATION","Quotation shared",row.get("releasedAt"),detail);
            String response="ACCEPTED".equals(row.get("status"))?"Quotation approved":"DECLINED".equals(row.get("status"))?"Quotation declined":null;
            addTimeline(events,"QUOTATION",response,row.get("respondedAt"),detail);
        }
        for(var row:a.rows("SELECT status,CASE WHEN status IN ('ISSUED','CONSUMED') THEN COALESCE(issued_at,created_at) WHEN status IN ('APPROVED','RESERVED','READY') THEN COALESCE(approved_at,created_at) ELSE COALESCE(submitted_at,created_at) END AS event_at FROM service_request_part_requests WHERE service_request_id=? AND status<>'DRAFT' ORDER BY id",job)) {
            String title=switch(String.valueOf(row.get("status"))) {
                case "SUBMITTED", "UNDER_REVIEW" -> "Parts requested"; case "APPROVED", "RESERVED", "READY" -> "Parts arranged";
                case "ISSUED", "CONSUMED" -> "Parts supplied"; case "REJECTED", "CANCELLED" -> "Parts request closed"; default -> null;
            };
            addTimeline(events,"PARTS",title,row.get("eventAt"),null);
        }
        for(var row:a.rows("SELECT invoice_number,issued_at FROM invoices WHERE service_request_id=? AND status IN ('ISSUED','PAID') ORDER BY id",job)) {
            addTimeline(events,"INVOICE","Invoice available",row.get("issuedAt"),row.get("invoiceNumber")==null?null:String.valueOf(row.get("invoiceNumber")));
        }
        for(var row:a.rows("SELECT p.verified_at,m.due_condition FROM payment_records p LEFT JOIN service_payment_milestones m ON m.invoice_id=p.invoice_id WHERE p.service_request_id=? AND p.invoice_id IS NOT NULL AND p.status IN ('SUCCEEDED','PARTIALLY_REFUNDED','REFUNDED') ORDER BY p.id",job)) {
            if(row.get("verifiedAt")!=null) {
                String title="ON_APPROVAL".equals(row.get("dueCondition"))||"BEFORE_REPAIR".equals(row.get("dueCondition"))?"Advance payment confirmed":"Payment confirmed";
                addTimeline(events,"PAYMENT",title,row.get("verifiedAt"),null);
                addTimeline(events,"RECEIPT","Payment receipt available",row.get("verifiedAt"),null);
            }
        }
        for(var row:a.rows("SELECT f.status,f.updated_at FROM payment_refunds f JOIN payment_records p ON p.id=f.payment_id WHERE p.service_request_id=? AND f.status='SUCCEEDED' ORDER BY f.id",job))
            addTimeline(events,"REFUND","Refund completed",row.get("updatedAt"),null);
        events.sort((left,right)->((java.time.LocalDateTime)left.get("occurredAt")).compareTo((java.time.LocalDateTime)right.get("occurredAt")));
        return events;
    }
    private static void addTimeline(List<Map<String,Object>> events,String kind,String title,Object timestamp,String detail) {
        if(title==null||timestamp==null)return;
        var event=new LinkedHashMap<String,Object>();event.put("kind",kind);event.put("title",title);event.put("occurredAt",timestamp);
        if(detail!=null&&!detail.equals("null"))event.put("detail",detail);
        events.add(event);
    }
    Map<String,Object> inspect(long job,Inspection in){
        a.job(job,true);long tech=a.technician();a.one("SELECT id FROM service_requests WHERE id=? FOR UPDATE",job);
        if(in==null)throw new CommerceException(400,"Inspection details are required");
        if(!in.draft()&&(in.estimatedDurationMinutes()==null||in.estimatedDurationMinutes()<1||in.estimatedDurationMinutes()>43200
                ||!FinanceAccess.oneOf(in.severity(),"LOW","MEDIUM","HIGH","CRITICAL")||!FinanceAccess.oneOf(in.safetyStatus(),"SAFE","RESTRICTED","UNSAFE")))throw new CommerceException(400,"Invalid inspection details");
        var evidence=in.evidenceIds()==null?List.<Long>of():in.evidenceIds().stream().distinct().toList();
        if(!in.draft()&&evidence.isEmpty()||evidence.size()>20)throw new CommerceException(400,"Attach between 1 and 20 files as job evidence.");
        for(Long id:evidence){if(id==null)throw new CommerceException(400,"Invalid evidence");a.evidence(job,id);}
        var drafts=a.rows("SELECT id FROM service_inspections WHERE service_request_id=? AND technician_profile_id=? AND status='DRAFT' ORDER BY id DESC",job,tech);
        long id=drafts.isEmpty()?a.insert("INSERT INTO service_inspections(service_request_id,technician_profile_id,reported_problem,observed_problem,root_cause,severity,safety_status,recommended_repair,required_labour,estimated_duration_minutes,status) VALUES(?,?,'','','','UNASSESSED','UNASSESSED','','',0,'DRAFT')",job,tech):((Number)drafts.get(0).get("id")).longValue();
        a.db.update("UPDATE service_inspections SET reported_problem=?,observed_problem=?,root_cause=?,severity=?,safety_status=?,recommended_repair=?,required_labour=?,estimated_duration_minutes=?,technician_remarks=?,required_spare_parts=?,status=? WHERE id=?",
                inspectionText(in.reportedProblem(),2000,in.draft()),inspectionText(in.observedProblem(),2000,in.draft()),inspectionText(in.rootCause(),2000,in.draft()),FinanceAccess.oneOf(in.severity(),"LOW","MEDIUM","HIGH","CRITICAL")?in.severity():"UNASSESSED",FinanceAccess.oneOf(in.safetyStatus(),"SAFE","RESTRICTED","UNSAFE")?in.safetyStatus():"UNASSESSED",inspectionText(in.recommendedRepair(),2000,in.draft()),inspectionText(in.requiredLabour(),1000,in.draft()),in.estimatedDurationMinutes()==null?0:Math.max(0,in.estimatedDurationMinutes()),in.technicianRemarks()==null?null:inspectionText(in.technicianRemarks(),2000,true),in.requiredSpareParts()==null?null:inspectionText(in.requiredSpareParts(),2000,true),in.draft()?"DRAFT":"SUBMITTED",id);
        a.db.update("DELETE FROM service_inspection_evidence WHERE inspection_id=?",id);
        for(long attachment:evidence)a.db.update("INSERT INTO service_inspection_evidence(inspection_id,attachment_id) VALUES(?,?)",id,attachment);
        a.event(job,in.draft()?"INSPECTION_DRAFT_SAVED":"INSPECTION_SUBMITTED","Inspection #"+id+" safety="+in.safetyStatus());return view(job);
    }
    private String inspectionText(String value,int max,boolean draft){if(draft&&(value==null||value.isBlank()))return "";return FinanceAccess.text(value,max);}
    Map<String,Object> completionTerms(long job,CompletionTerms in){a.admin();var request=a.one("SELECT status FROM service_requests WHERE id=? FOR UPDATE",job);if(!"COMPLETED".equals(request.get("status")))throw new CommerceException(409,"Job completion is required before final handover terms");int version=a.db.queryForObject("SELECT COALESCE(MAX(version_number),0)+1 FROM service_completion_terms WHERE service_request_id=?",Integer.class,job);a.db.update("INSERT INTO service_completion_terms(service_request_id,version_number,warranty_details,handover_notes,reviewed_by_user_id) VALUES(?,?,?,?,?)",job,version,FinanceAccess.text(in.warrantyDetails(),2000),FinanceAccess.text(in.handoverNotes(),2000),a.actor().getId());a.event(job,"COMPLETION_TERMS_PUBLISHED","Published completion terms version "+version);return view(job);}
    Map<String,Object> reviewInspection(long id,Review in){
        a.admin();var row=a.one("SELECT * FROM service_inspections WHERE id=? FOR UPDATE",id);long job=((Number)row.get("serviceRequestId")).longValue();
        String reason=FinanceAccess.text(in.reason(),1000);
        if("DRAFT".equals(row.get("status")))throw new CommerceException(409,"Submit the inspection before administrative review");
        if(!"SUBMITTED".equals(row.get("status"))&&!in.clearSafety())throw new CommerceException(409,"Inspection was already reviewed");
        if(in.clearSafety()&&!in.approve())throw new CommerceException(400,"Safety clearance requires approval");
        a.db.update("UPDATE service_inspections SET status=?,safety_status=?,review_reason=?,reviewed_at=CURRENT_TIMESTAMP,reviewed_by_user_id=? WHERE id=?",in.approve()?"APPROVED":"REJECTED",in.clearSafety()?"CLEARED":row.get("safetyStatus"),reason,a.actor().getId(),id);
        a.event(job,in.clearSafety()?"SAFETY_CLEARED":"INSPECTION_REVIEWED",reason);return view(job);
    }
    Map<String,Object> requestWork(long job,Work in){a.job(job,true);long tech=a.technician();a.db.update("INSERT INTO service_work_approvals(service_request_id,technician_profile_id,description,reason) VALUES(?,?,?,?)",job,tech,FinanceAccess.text(in.description(),2000),FinanceAccess.text(in.reason(),2000));a.event(job,"ADDITIONAL_WORK_REQUESTED",in.description());return view(job);}
    Map<String,Object> reviewWork(long id,WorkReview in){
        a.admin();var row=a.one("SELECT * FROM service_work_approvals WHERE id=? FOR UPDATE",id);long job=((Number)row.get("serviceRequestId")).longValue();
        if(!"PENDING_ADMIN".equals(row.get("status")))throw new CommerceException(409,"Work request was already reviewed");
        String reason=FinanceAccess.text(in.reason(),1000);
        if(!in.reject()&&(in.quotationId()==null||a.count("SELECT COUNT(*) FROM service_quotations WHERE id=? AND service_request_id=? AND status='ISSUED' AND release_status='RELEASED'",in.quotationId(),job)==0))throw new CommerceException(400,"Link a released quotation for this job");
        a.db.update("UPDATE service_work_approvals SET status=?,quotation_id=?,review_reason=? WHERE id=?",in.reject()?"REJECTED":"AWAITING_CUSTOMER",in.reject()?null:in.quotationId(),reason,id);a.event(job,"ADDITIONAL_WORK_REVIEWED",reason);return view(job);
    }
    Map<String,Object> usage(long job,Usage in){
        a.job(job,true);long tech=a.technician();
        if(in.quantity()<1||!FinanceAccess.oneOf(in.action(),"RECEIVED","INSTALLED","RETURNED"))throw new CommerceException(400,"Invalid part usage");
        String key=FinanceAccess.text(in.idempotencyKey(),80), reason=FinanceAccess.text(in.reason(),1000);
        var line=a.one("SELECT l.*,r.technician_profile_id,r.status FROM service_request_part_lines l JOIN service_request_part_requests r ON r.id=l.request_id WHERE l.id=? AND r.service_request_id=? FOR UPDATE",in.lineId(),job);
        if(((Number)line.get("technicianProfileId")).longValue()!=tech||!"ISSUED".equals(line.get("status")))throw new CommerceException(409,"Parts must be issued to this technician first");
        var prior=a.rows("SELECT * FROM job_part_usage WHERE actor_user_id=? AND idempotency_key=?",a.actor().getId(),key);
        if(!prior.isEmpty()){var p=prior.get(0);if(((Number)p.get("partLineId")).longValue()!=in.lineId()||!p.get("action").equals(in.action())||((Number)p.get("quantity")).intValue()!=in.quantity()||!p.get("reason").equals(reason))throw new CommerceException(409,"Part action key already used");return view(job);}
        long received=quantity(in.lineId(),"RECEIVED"),installed=quantity(in.lineId(),"INSTALLED"),returned=quantity(in.lineId(),"RETURNED");
        if(in.action().equals("RECEIVED")?received+in.quantity()>((Number)line.get("issuedQuantity")).longValue():installed+returned+in.quantity()>received)throw new CommerceException(409,"Part quantity exceeds the available job quantity");
        if(in.action().equals("INSTALLED"))a.requireWorkApproved(job);
        a.db.update("INSERT INTO job_part_usage(part_line_id,action,quantity,reason,actor_user_id,idempotency_key) VALUES(?,?,?,?,?,?)",in.lineId(),in.action(),in.quantity(),reason,a.actor().getId(),key);
        if(!in.action().equals("RECEIVED")){
            a.one("SELECT id FROM part_items WHERE id=? FOR UPDATE",line.get("partItemId"));
            if(in.action().equals("RETURNED"))a.db.update("UPDATE part_items SET quantity_on_hand=quantity_on_hand+?,version=version+1 WHERE id=?",in.quantity(),line.get("partItemId"));
            a.db.update("INSERT INTO part_stock_movements(part_item_id,movement_type,quantity,reason,service_request_id,actor_user_id,created_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",line.get("partItemId"),in.action().equals("INSTALLED")?"CONSUME":"RETURN",in.quantity(),reason,job,a.actor().getId());
        }
        a.event(job,"PART_"+in.action(),reason);return view(job);
    }
    private long quantity(long line,String action){return a.count("SELECT COALESCE(SUM(quantity),0) FROM job_part_usage WHERE part_line_id=? AND action=?",line,action);}
    List<Map<String,Object>> purchase(Purchase in){
        a.admin();if(in.quantity()<1||in.quantity()>100000)throw new CommerceException(400,"Invalid purchase quantity");FinanceAccess.money(in.unitCost(),true);
        a.one("SELECT id FROM part_items WHERE id=? FOR UPDATE",in.partItemId());if(in.serviceRequestId()!=null)a.job(in.serviceRequestId(),false);
        String reference=FinanceAccess.text(in.reference(),120),supplier=FinanceAccess.text(in.supplierName(),180);
        var prior=a.rows("SELECT * FROM supplier_purchases WHERE reference=?",reference);
        if(!prior.isEmpty())throw new CommerceException(409,"Purchase reference already recorded");
        a.db.update("INSERT INTO supplier_purchases(part_item_id,service_request_id,supplier_name,reference,quantity,unit_cost,actor_user_id) VALUES(?,?,?,?,?,?,?)",in.partItemId(),in.serviceRequestId(),supplier,reference,in.quantity(),in.unitCost(),a.actor().getId());
        a.db.update("UPDATE part_items SET quantity_on_hand=quantity_on_hand+?,version=version+1 WHERE id=?",in.quantity(),in.partItemId());
        a.db.update("INSERT INTO part_stock_movements(part_item_id,movement_type,quantity,reason,service_request_id,actor_user_id,created_at) VALUES(?,'RESTOCK',?,?,?,?,CURRENT_TIMESTAMP)",in.partItemId(),in.quantity(),"Purchase "+reference,in.serviceRequestId(),a.actor().getId());return purchases();
    }
    List<Map<String,Object>> purchases(){a.admin();return a.rows("SELECT s.*,p.name AS part_name FROM supplier_purchases s JOIN part_items p ON p.id=s.part_item_id ORDER BY s.id DESC LIMIT 200");}
    Map<String,Object> hold(long job,Hold in){a.admin();a.job(job,false);String reason=FinanceAccess.text(in.reason(),1000);if(in.resolveId()==null)a.db.update("INSERT INTO service_finance_holds(service_request_id,reason,actor_user_id) VALUES(?,?,?)",job,reason,a.actor().getId());else if(a.db.update("UPDATE service_finance_holds SET resolved_at=CURRENT_TIMESTAMP WHERE id=? AND service_request_id=? AND resolved_at IS NULL",in.resolveId(),job)==0)throw new CommerceException(409,"Hold is not open");a.event(job,in.resolveId()==null?"FINANCE_HOLD":"FINANCE_HOLD_RESOLVED",reason);return view(job);}
}
