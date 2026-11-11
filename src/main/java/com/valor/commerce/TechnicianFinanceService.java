package com.valor.commerce;

import java.math.*;
import java.time.*;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class TechnicianFinanceService {
    record Rule(String serviceType,String basisType,BigDecimal percentageRate,BigDecimal fixedAmount,LocalDate effectiveFrom,LocalDate effectiveUntil) {}
    record Basis(long invoiceId,BigDecimal netLabourAmount,String reason) {}
    record Accrue(long technicianProfileId,long serviceRequestId,Long invoiceId,long ruleVersionId,String eligibleWorkKey,BigDecimal eligibleBasis,String reason) {}
    record Decision(boolean approve,String reason) {}
    record Adjustment(BigDecimal amount,Long refundId,String reason) {}
    record Settings(String payoutSchedule,boolean manualPayoutEnabled) {}
    record Batch(long technicianProfileId,LocalDate periodStart,LocalDate periodEnd,String reference,List<Long> commissionIds) {}
    record Transfer(String action,String reference,Long evidenceAttachmentId,String reason) {}
    record Expense(long serviceRequestId,String category,String description,BigDecimal amount,LocalDate purchaseDate,long attachmentId,String reason,String idempotencyKey) {}
    record ExpenseReview(String action,String reason,String reimbursementReference) {}
    final FinanceAccess a;
    TechnicianFinanceService(FinanceAccess a){this.a=a;}

    List<Map<String,Object>> rules(){a.admin();return a.rows("SELECT * FROM commission_rule_versions ORDER BY id DESC LIMIT 200");}
    Map<String,Object> rule(Rule in){
        a.admin();if(in==null||!FinanceAccess.oneOf(in.serviceType(),"ROUTINE_MAINTENANCE","BREAKDOWN","EMERGENCY","INSPECTION","INSTALLATION","MODERNIZATION")||in.effectiveFrom()==null||in.effectiveUntil()!=null&&in.effectiveUntil().isBefore(in.effectiveFrom()))throw new CommerceException(400,"Invalid commission rule dates or category");
        if("NET_LABOUR_PERCENT".equals(in.basisType())){if(in.percentageRate()==null||in.percentageRate().signum()<=0||in.percentageRate().compareTo(new BigDecimal("100"))>0||in.percentageRate().scale()>4||in.fixedAmount()!=null)throw new CommerceException(400,"Invalid commission percentage");}
        else if("FIXED_VISIT".equals(in.basisType())){FinanceAccess.money(in.fixedAmount(),false);if(in.percentageRate()!=null)throw new CommerceException(400,"Fixed rules cannot have a percentage");}
        else throw new CommerceException(400,"Invalid commission basis");
        // Serialize version allocation without introducing a mutable historical rule.
        a.one("SELECT id FROM users WHERE id=? FOR UPDATE",a.actor().getId());
        int version=a.db.queryForObject("SELECT COALESCE(MAX(version_number),0)+1 FROM commission_rule_versions WHERE service_type=?",Integer.class,in.serviceType());
        long id=a.insert("INSERT INTO commission_rule_versions(service_type,version_number,basis_type,percentage_rate,fixed_amount,effective_from,effective_until,created_by_user_id) VALUES(?,?,?,?,?,?,?,?)",in.serviceType(),version,in.basisType(),in.percentageRate(),in.fixedAmount(),in.effectiveFrom(),in.effectiveUntil(),a.actor().getId());return a.one("SELECT * FROM commission_rule_versions WHERE id=?",id);
    }
    Map<String,Object> approveRule(long id){
        a.admin();var r=a.one("SELECT * FROM commission_rule_versions WHERE id=? FOR UPDATE",id);
        if(!"DRAFT".equals(r.get("status")))throw new CommerceException(409,"Only a draft rule can be approved");
        // Lock the category's rules; overlapping approved dates are never ambiguous.
        a.rows("SELECT id FROM commission_rule_versions WHERE service_type=? ORDER BY id FOR UPDATE",r.get("serviceType"));
        if(a.count("SELECT COUNT(*) FROM commission_rule_versions WHERE service_type=? AND status='APPROVED' AND effective_from<=COALESCE(?,CAST('9999-12-31' AS DATE)) AND (effective_until IS NULL OR effective_until>=?)",r.get("serviceType"),r.get("effectiveUntil"),r.get("effectiveFrom"))>0)throw new CommerceException(409,"An approved rule overlaps these dates");
        a.db.update("UPDATE commission_rule_versions SET status='APPROVED',approved_by_user_id=?,approved_at=CURRENT_TIMESTAMP WHERE id=?",a.actor().getId(),id);return a.one("SELECT * FROM commission_rule_versions WHERE id=?",id);
    }
    Map<String,Object> retireRule(long id){a.admin();a.one("SELECT id FROM commission_rule_versions WHERE id=? FOR UPDATE",id);a.db.update("UPDATE commission_rule_versions SET status='RETIRED' WHERE id=?",id);return a.one("SELECT * FROM commission_rule_versions WHERE id=?",id);}
    Map<String,Object> basis(Basis in){
        a.admin();var invoice=a.one("SELECT * FROM invoices WHERE id=? FOR UPDATE",in.invoiceId());BigDecimal basis=FinanceAccess.money(in.netLabourAmount(),true);
        if(!"INR".equals(invoice.get("currency")))throw new CommerceException(400,"Commission ledger requires an INR invoice");
        if(basis.compareTo(((BigDecimal)invoice.get("totalAmount")).subtract((BigDecimal)invoice.get("taxAmount")))>0)throw new CommerceException(400,"Net labour basis exceeds the invoice before tax");
        if(a.count("SELECT COUNT(*) FROM technician_commissions WHERE invoice_id=?",in.invoiceId())>0)throw new CommerceException(409,"Commission basis is immutable after accrual; use an adjustment");
        if(a.count("SELECT COUNT(*) FROM invoice_commission_bases WHERE invoice_id=?",in.invoiceId())>0)throw new CommerceException(409,"Invoice labour basis was already approved");
        a.db.update("INSERT INTO invoice_commission_bases(invoice_id,net_labour_amount,reason,approved_by_user_id) VALUES(?,?,?,?)",in.invoiceId(),basis,FinanceAccess.text(in.reason(),1000),a.actor().getId());return a.one("SELECT * FROM invoice_commission_bases WHERE invoice_id=?",in.invoiceId());
    }
    Map<String,Object> accrue(Accrue in){
        a.admin();a.one("SELECT id FROM service_requests WHERE id=? FOR UPDATE",in.serviceRequestId());var job=a.job(in.serviceRequestId(),false);
        eligible(in.serviceRequestId(),in.technicianProfileId(),in.invoiceId());
        var rule=a.one("SELECT * FROM commission_rule_versions WHERE id=?",in.ruleVersionId());
        LocalDate date=((LocalDateTime)job.get("completedAt")).toLocalDate();
        if(!"APPROVED".equals(rule.get("status"))||!rule.get("serviceType").equals(job.get("serviceType"))||date.isBefore((LocalDate)rule.get("effectiveFrom"))||rule.get("effectiveUntil")!=null&&date.isAfter((LocalDate)rule.get("effectiveUntil")))throw new CommerceException(409,"No approved rule applies to this completion date");
        String key=FinanceAccess.text(in.eligibleWorkKey(),80), reason=FinanceAccess.text(in.reason(),1000);
        if(a.count("SELECT COUNT(*) FROM technician_commissions WHERE technician_profile_id=? AND service_request_id=? AND eligible_work_key=?",in.technicianProfileId(),in.serviceRequestId(),key)>0)throw new CommerceException(409,"Eligible work was already accrued");
        BigDecimal basis=FinanceAccess.money(in.eligibleBasis(),true),rate,amount;
        if("NET_LABOUR_PERCENT".equals(rule.get("basisType"))){
            if(in.invoiceId()==null)throw new CommerceException(400,"Percentage commission requires an invoice");
            var approved=a.one("SELECT * FROM invoice_commission_bases WHERE invoice_id=?",in.invoiceId());
            BigDecimal used=a.sum("SELECT COALESCE(SUM(eligible_basis),0) FROM technician_commissions WHERE invoice_id=?",in.invoiceId());
            if(used.add(basis).compareTo((BigDecimal)approved.get("netLabourAmount"))>0)throw new CommerceException(409,"Technician allocations exceed approved net labour");
            rate=(BigDecimal)rule.get("percentageRate");amount=basis.multiply(rate).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
        }else{
            rate=(BigDecimal)rule.get("fixedAmount");amount=basis;
            BigDecimal used=a.sum("SELECT COALESCE(SUM(calculated_amount),0) FROM technician_commissions WHERE service_request_id=? AND rule_version_id=?",in.serviceRequestId(),in.ruleVersionId());
            if(used.add(amount).compareTo(rate)>0)throw new CommerceException(409,"Fixed visit allocations exceed the approved rule amount");
        }
        if(amount.signum()<=0)throw new CommerceException(400,"Calculated commission must be positive");
        long id=a.insert("INSERT INTO technician_commissions(technician_profile_id,service_request_id,invoice_id,rule_version_id,eligible_work_key,eligible_basis,rate_snapshot,calculated_amount,eligibility_reason) VALUES(?,?,?,?,?,?,?,?,?)",in.technicianProfileId(),in.serviceRequestId(),in.invoiceId(),in.ruleVersionId(),key,basis,rate,amount,reason);
        a.event(in.serviceRequestId(),"COMMISSION_ACCRUED",reason);return commission(id);
    }
    private void eligible(long job,long technician,Long invoice){
        a.requireWorkApproved(job);
        if(a.count("SELECT COUNT(*) FROM service_requests WHERE id=? AND status='COMPLETED' AND completed_at IS NOT NULL",job)==0||a.count("SELECT COUNT(*) FROM technician_assignments WHERE service_request_id=? AND technician_id=? AND status='COMPLETED'",job,technician)==0||a.count("SELECT COUNT(*) FROM service_reports WHERE service_request_id=?",job)==0)throw new CommerceException(409,"Accepted completion and service evidence are required");
        if(invoice!=null){
            if(a.count("SELECT COUNT(*) FROM invoices i WHERE i.id=? AND i.service_request_id=? AND i.currency='INR' AND i.status='PAID' AND EXISTS(SELECT 1 FROM payment_records p WHERE p.invoice_id=i.id AND p.status='SUCCEEDED')",invoice,job)==0)throw new CommerceException(409,"Customer payment must clear first");
            if(a.count("SELECT COUNT(*) FROM payment_refunds f JOIN payment_records p ON p.id=f.payment_id WHERE p.invoice_id=? AND f.status IN ('REQUESTED','PROCESSING','SUCCEEDED')",invoice)>0)throw new CommerceException(409,"Refund review blocks new commission accrual");
        }else{
            var covered=a.rows("SELECT d.contract_id FROM service_request_coverage_decisions d JOIN service_requests r ON r.id=d.service_request_id JOIN amc_contracts c ON c.id=d.contract_id AND c.lift_id=r.lift_id WHERE d.service_request_id=? AND d.scope='VISIT' AND d.outcome='COVERED' AND d.version_number=(SELECT MAX(v.version_number) FROM service_request_coverage_decisions v WHERE v.service_request_id=d.service_request_id AND v.scope='VISIT') AND d.service_date>=c.start_date AND d.service_date<=c.end_date",job);
            if(covered.isEmpty())throw new CommerceException(409,"Confirm this visit is included in the maintenance plan before approving a fixed visit commission");
            long contract=((Number)covered.get(0).get("contractId")).longValue();
            if(a.count("SELECT COUNT(*) FROM payment_records WHERE amc_contract_id=? AND status='SUCCEEDED'",contract)==0)
                throw new CommerceException(409,"Confirm the maintenance plan payment before approving this commission");
            if(a.count("SELECT COUNT(*) FROM payment_refunds f JOIN payment_records p ON p.id=f.payment_id WHERE p.amc_contract_id=? AND f.status IN ('REQUESTED','PROCESSING','SUCCEEDED')",contract)>0)
                throw new CommerceException(409,"Review the maintenance plan refund before approving this commission");
        }
    }
    Map<String,Object> decideCommission(long id,Decision in){
        a.admin();var row=a.one("SELECT * FROM technician_commissions WHERE id=? FOR UPDATE",id);String reason=FinanceAccess.text(in.reason(),1000);
        if(!List.of("PENDING_APPROVAL","HELD").contains(row.get("status")))throw new CommerceException(409,"Commission is not awaiting approval");
        if(in.approve())eligible(((Number)row.get("serviceRequestId")).longValue(),((Number)row.get("technicianProfileId")).longValue(),row.get("invoiceId")==null?null:((Number)row.get("invoiceId")).longValue());
        a.db.update("UPDATE technician_commissions SET status=?,approved_amount=?,approved_by_user_id=?,approved_at=CURRENT_TIMESTAMP WHERE id=?",in.approve()?"READY":"HELD",in.approve()?row.get("calculatedAmount"):null,a.actor().getId(),id);a.event(((Number)row.get("serviceRequestId")).longValue(),"COMMISSION_REVIEWED",reason);return commission(id);
    }
    Map<String,Object> adjust(long id,Adjustment in){
        a.admin();var row=a.one("SELECT * FROM technician_commissions WHERE id=? FOR UPDATE",id);BigDecimal amount=in.amount();
        if(amount==null||amount.signum()==0||amount.scale()>2||amount.precision()>12)throw new CommerceException(400,"Invalid adjustment amount");
        if(a.count("SELECT COUNT(*) FROM technician_payout_items WHERE commission_id=? AND status='ACTIVE'",id)>0)throw new CommerceException(409,"Commission is locked in a payout batch");
        if(in.refundId()!=null&&(amount.signum()>0||a.count("SELECT COUNT(*) FROM payment_refunds f JOIN payment_records p ON p.id=f.payment_id WHERE f.id=? AND p.invoice_id=? AND f.status='SUCCEEDED'",in.refundId(),row.get("invoiceId"))==0))throw new CommerceException(400,"Refund adjustment must reduce commission for a confirmed related refund");
        BigDecimal total=((BigDecimal)row.get("calculatedAmount")).add(a.sum("SELECT COALESCE(SUM(adjustment_amount),0) FROM commission_adjustments WHERE commission_id=?",id)).add(amount);
        if(total.signum()<0)throw new CommerceException(400,"Adjustment exceeds the commission amount");
        a.db.update("INSERT INTO commission_adjustments(commission_id,source_payment_refund_id,adjustment_amount,reason,actor_user_id) VALUES(?,?,?,?,?)",id,in.refundId(),amount,FinanceAccess.text(in.reason(),500),a.actor().getId());a.event(((Number)row.get("serviceRequestId")).longValue(),"COMMISSION_ADJUSTED",in.reason());return commission(id);
    }
    private Map<String,Object> commission(long id){return a.one("SELECT c.*,r.service_type,r.basis_type FROM technician_commissions c JOIN commission_rule_versions r ON r.id=c.rule_version_id WHERE c.id=?",id);}
    List<Map<String,Object>> commissions(){a.admin();return a.rows("SELECT c.*,r.service_type,r.basis_type,s.completed_at AS service_completed_at,COALESCE((SELECT SUM(x.adjustment_amount) FROM commission_adjustments x WHERE x.commission_id=c.id),0) AS adjustments FROM technician_commissions c JOIN commission_rule_versions r ON r.id=c.rule_version_id JOIN service_requests s ON s.id=c.service_request_id ORDER BY c.id DESC LIMIT 300");}
    Map<String,Object> settings(){a.admin();var rows=a.rows("SELECT * FROM technician_finance_settings WHERE id=1");return rows.isEmpty()?Map.of("payoutSchedule","MANUAL","manualPayoutEnabled",false):rows.get(0);}
    Map<String,Object> settings(Settings in){a.admin();if(!FinanceAccess.oneOf(in.payoutSchedule(),"WEEKLY","FORTNIGHTLY","MONTHLY","MANUAL"))throw new CommerceException(400,"Invalid payout schedule");a.one("SELECT id FROM users WHERE id=? FOR UPDATE",a.actor().getId());if(a.count("SELECT COUNT(*) FROM technician_finance_settings WHERE id=1")==0)a.db.update("INSERT INTO technician_finance_settings(id,payout_schedule,manual_payout_enabled,updated_by_user_id) VALUES(1,?,?,?)",in.payoutSchedule(),in.manualPayoutEnabled(),a.actor().getId());else a.db.update("UPDATE technician_finance_settings SET payout_schedule=?,manual_payout_enabled=?,updated_by_user_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=1",in.payoutSchedule(),in.manualPayoutEnabled(),a.actor().getId());return settings();}
    Map<String,Object> batch(Batch in){
        a.admin();if(in.periodStart()==null||in.periodEnd()==null||in.periodEnd().isBefore(in.periodStart())||in.commissionIds()==null||in.commissionIds().isEmpty()||in.commissionIds().size()>100)throw new CommerceException(400,"Invalid payout period or commissions");
        a.one("SELECT id FROM technician_profiles WHERE id=? FOR UPDATE",in.technicianProfileId());
        List<Long> ids=in.commissionIds().stream().distinct().sorted().toList();BigDecimal total=BigDecimal.ZERO;var amounts=new LinkedHashMap<Long,BigDecimal>();
        for(long id:ids){var c=a.one("SELECT * FROM technician_commissions WHERE id=? FOR UPDATE",id);
            if(((Number)c.get("technicianProfileId")).longValue()!=in.technicianProfileId()||!"READY".equals(c.get("status")))throw new CommerceException(409,"Only this technician's ready commissions can enter a payout");
            LocalDate date=((LocalDateTime)c.get("createdAt")).toLocalDate();if(date.isBefore(in.periodStart())||date.isAfter(in.periodEnd()))throw new CommerceException(400,"Commission is outside the payout period");
            payoutEligible(c);BigDecimal amount=((BigDecimal)c.get("approvedAmount")).add(a.sum("SELECT COALESCE(SUM(adjustment_amount),0) FROM commission_adjustments WHERE commission_id=?",id));
            if(amount.signum()<=0)throw new CommerceException(409,"Commission has no positive payable amount");amounts.put(id,amount);total=total.add(amount);
        }
        // Any paid commission with a new refund needs an explicit, auditable finance decision first.
        for(var paid:a.rows("SELECT * FROM technician_commissions WHERE technician_profile_id=? AND status='PAID'",in.technicianProfileId()))payoutEligible(paid);
        var offsets=a.rows("SELECT x.* FROM commission_adjustments x JOIN technician_commissions c ON c.id=x.commission_id WHERE c.technician_profile_id=? AND c.status='PAID' AND NOT EXISTS(SELECT 1 FROM technician_payout_adjustments o WHERE o.adjustment_id=x.id AND o.status IN ('ACTIVE','PAID')) ORDER BY x.id FOR UPDATE",in.technicianProfileId());
        for(var offset:offsets)total=total.add((BigDecimal)offset.get("adjustmentAmount"));
        if(total.signum()<=0)throw new CommerceException(409,"Outstanding adjustments leave no positive payout");
        long batch=a.insert("INSERT INTO technician_payout_batches(batch_reference,period_start,period_end,total_amount,status,created_by_user_id) VALUES(?,?,?,?,'PENDING_APPROVAL',?)",FinanceAccess.text(in.reference(),80),in.periodStart(),in.periodEnd(),total,a.actor().getId());
        for(var entry:amounts.entrySet())a.db.update("INSERT INTO technician_payout_items(batch_id,commission_id,amount) VALUES(?,?,?)",batch,entry.getKey(),entry.getValue());
        for(var offset:offsets)a.db.update("INSERT INTO technician_payout_adjustments(batch_id,adjustment_id,amount) VALUES(?,?,?)",batch,offset.get("id"),offset.get("adjustmentAmount"));
        payoutEvent(batch,"CREATED",null,"PENDING_APPROVAL","Payout created");return payout(batch);
    }
    private void payoutEligible(Map<String,Object> c){
        long job=((Number)c.get("serviceRequestId")).longValue();a.requireWorkApproved(job);
        if(c.get("invoiceId")!=null&&a.count("SELECT COUNT(*) FROM payment_refunds f JOIN payment_records p ON p.id=f.payment_id WHERE p.invoice_id=? AND (f.status IN ('REQUESTED','PROCESSING') OR (f.status='SUCCEEDED' AND NOT EXISTS(SELECT 1 FROM commission_adjustments x WHERE x.commission_id=? AND x.source_payment_refund_id=f.id)))",c.get("invoiceId"),c.get("id"))>0)throw new CommerceException(409,"Related refund requires commission adjustment review");
        if(c.get("invoiceId")==null){
            var covered=a.rows("SELECT d.contract_id FROM service_request_coverage_decisions d JOIN service_requests r ON r.id=d.service_request_id JOIN amc_contracts plan ON plan.id=d.contract_id AND plan.lift_id=r.lift_id WHERE d.service_request_id=? AND d.scope='VISIT' AND d.outcome='COVERED' AND d.version_number=(SELECT MAX(v.version_number) FROM service_request_coverage_decisions v WHERE v.service_request_id=d.service_request_id AND v.scope='VISIT') AND d.service_date>=plan.start_date AND d.service_date<=plan.end_date",job);
            if(covered.isEmpty())throw new CommerceException(409,"Confirm this visit is included in the maintenance plan before paying its commission");
            long contract=((Number)covered.get(0).get("contractId")).longValue();
            if(a.count("SELECT COUNT(*) FROM payment_records WHERE amc_contract_id=? AND status='SUCCEEDED'",contract)==0)
                throw new CommerceException(409,"Confirm the maintenance plan payment before paying this commission");
            if(a.count("SELECT COUNT(*) FROM payment_refunds f JOIN payment_records p ON p.id=f.payment_id WHERE p.amc_contract_id=? AND f.status IN ('REQUESTED','PROCESSING','SUCCEEDED')",contract)>0)
                throw new CommerceException(409,"Review the maintenance plan refund before paying this commission");
        }
    }
    Map<String,Object> transfer(long id,Transfer in){
        FinanceAccess.text(in.action(),40);
        a.admin();var b=a.one("SELECT * FROM technician_payout_batches WHERE id=? FOR UPDATE",id);String before=(String)b.get("status"),next;String reason=FinanceAccess.text(in.reason(),1000);
        switch(in.action()){
            case "APPROVE" -> {if(!before.equals("PENDING_APPROVAL"))throw new CommerceException(409,"Batch is not awaiting approval");validateBatch(id);a.db.update("UPDATE technician_payout_batches SET approved_by_user_id=?,approved_at=CURRENT_TIMESTAMP WHERE id=?",a.actor().getId(),id);next="APPROVED";}
            case "INITIATE" -> {if(!before.equals("APPROVED")||!Boolean.TRUE.equals(settings().get("manualPayoutEnabled")))throw new CommerceException(409,"Approved manual payout configuration is required");validateBatch(id);a.db.update("UPDATE technician_payout_batches SET external_transfer_reference=?,initiated_by_user_id=? WHERE id=?",FinanceAccess.text(in.reference(),120),a.actor().getId(),id);next="PROCESSING";}
            case "CONFIRM" -> {
                if((!before.equals("PROCESSING")&&!before.equals("RECONCILIATION_REQUIRED"))||in.evidenceAttachmentId()==null)throw new CommerceException(409,"Processing payout and bank evidence are required");
                if(Objects.equals(((Number)b.get("initiatedByUserId")).longValue(),a.actor().getId()))throw new CommerceException(403,"A different administrator must verify the bank transfer");
                long evidence=in.evidenceAttachmentId();
                if(a.count("SELECT COUNT(*) FROM finance_evidence WHERE id=? AND payout_batch_id=?",evidence,id)==0)throw new CommerceException(400,"Bank evidence must be a private attachment on this payout batch");
                a.db.update("UPDATE technician_payout_batches SET provider_confirmation_reference=?,evidence_attachment_id=?,confirmed_at=CURRENT_TIMESTAMP WHERE id=?",FinanceAccess.text(in.reference(),120),evidence,id);
                a.db.update("UPDATE technician_commissions SET status='PAID' WHERE id IN(SELECT commission_id FROM technician_payout_items WHERE batch_id=? AND status='ACTIVE')",id);
                a.db.update("UPDATE technician_payout_items SET status='PAID' WHERE batch_id=? AND status='ACTIVE'",id);a.db.update("UPDATE technician_payout_adjustments SET status='PAID' WHERE batch_id=? AND status='ACTIVE'",id);next="SUCCEEDED";
            }
            case "FAIL","CANCEL" -> {if(List.of("SUCCEEDED","CANCELLED","FAILED").contains(before))throw new CommerceException(409,"Batch is already final");if(before.equals("PROCESSING")||before.equals("RECONCILIATION_REQUIRED"))throw new CommerceException(409,"An initiated transfer requires reconciliation before retry");next=in.action().equals("FAIL")?"FAILED":"CANCELLED";releaseBatch(id);}
            case "RECONCILE_FAILED" -> {if(!before.equals("PROCESSING")&&!before.equals("RECONCILIATION_REQUIRED"))throw new CommerceException(409,"Batch does not need bank reconciliation");FinanceAccess.text(in.reference(),120);if(Objects.equals(b.get("initiatedByUserId"),a.actor().getId()))throw new CommerceException(403,"A different administrator must reconcile the transfer");if(in.evidenceAttachmentId()==null||a.count("SELECT COUNT(*) FROM finance_evidence WHERE id=? AND payout_batch_id=?",in.evidenceAttachmentId(),id)==0)throw new CommerceException(400,"Private bank evidence is required before releasing a failed transfer");a.db.update("UPDATE technician_payout_batches SET provider_confirmation_reference=?,evidence_attachment_id=? WHERE id=?",in.reference().trim(),in.evidenceAttachmentId(),id);next="FAILED";releaseBatch(id);}
            case "UNCERTAIN" -> {if(!before.equals("PROCESSING"))throw new CommerceException(409,"Only an initiated payout can have an uncertain result");next="RECONCILIATION_REQUIRED";}
            default -> throw new CommerceException(400,"Invalid payout action");
        }
        a.db.update("UPDATE technician_payout_batches SET status=? WHERE id=?",next,id);payoutEvent(id,in.action(),before,next,reason);
        if(next.equals("SUCCEEDED")){var users=a.db.queryForList("SELECT DISTINCT t.user_id FROM technician_payout_items i JOIN technician_commissions c ON c.id=i.commission_id JOIN technician_profiles t ON t.id=c.technician_profile_id WHERE i.batch_id=?",Long.class,id);for(long user:users)a.db.update("INSERT INTO finance_notification_outbox(payout_batch_id,user_id,message) VALUES(?,?,?)",id,user,"Payout "+b.get("batchReference")+" was verified by Valor.");}return payout(id);
    }
    private void releaseBatch(long id){a.db.update("UPDATE technician_payout_items SET status='RELEASED' WHERE batch_id=? AND status='ACTIVE'",id);a.db.update("UPDATE technician_payout_adjustments SET status='RELEASED' WHERE batch_id=? AND status='ACTIVE'",id);}
    private void validateBatch(long id){for(var c:a.rows("SELECT c.* FROM technician_commissions c JOIN technician_payout_items i ON i.commission_id=c.id WHERE i.batch_id=? AND i.status='ACTIVE'",id)){if(!"READY".equals(c.get("status")))throw new CommerceException(409,"Batch contains a commission that is no longer ready");payoutEligible(c);}}
    private void payoutEvent(long batch,String type,String before,String after,String reason){a.db.update("INSERT INTO technician_payout_events(batch_id,event_type,previous_status,new_status,actor_user_id,reason) VALUES(?,?,?,?,?,?)",batch,type,before,after,a.actor().getId(),reason);a.audit("TECHNICIAN_PAYOUT_"+type,"PAYOUT_BATCH",batch,reason);}
    Map<String,Object> payout(long id){a.admin();var b=a.one("SELECT * FROM technician_payout_batches WHERE id=?",id);b.put("items",a.rows("SELECT i.*,c.service_request_id,c.technician_profile_id FROM technician_payout_items i JOIN technician_commissions c ON c.id=i.commission_id WHERE i.batch_id=?",id));b.put("events",a.rows("SELECT * FROM technician_payout_events WHERE batch_id=? ORDER BY id",id));b.put("evidence",a.rows("SELECT id,original_filename,content_type FROM finance_evidence WHERE payout_batch_id=?",id));return b;}
    List<Map<String,Object>> payouts(){a.admin();return a.rows("SELECT * FROM technician_payout_batches ORDER BY id DESC LIMIT 200");}
    Map<String,Object> earnings(){
        long tech=a.technician();var entries=a.rows("SELECT c.*,r.service_type,r.basis_type,s.completed_at AS service_completed_at,COALESCE((SELECT SUM(x.adjustment_amount) FROM commission_adjustments x WHERE x.commission_id=c.id),0) AS adjustments,(SELECT b.batch_reference FROM technician_payout_items i JOIN technician_payout_batches b ON b.id=i.batch_id WHERE i.commission_id=c.id AND i.status IN ('ACTIVE','PAID') LIMIT 1) AS payout_reference FROM technician_commissions c JOIN commission_rule_versions r ON r.id=c.rule_version_id JOIN service_requests s ON s.id=c.service_request_id WHERE c.technician_profile_id=? ORDER BY c.id DESC",tech);
        var summary=new LinkedHashMap<String,BigDecimal>();for(String key:List.of("earned","pendingCollection","pendingApproval","ready","paid","held","adjustments"))summary.put(key,BigDecimal.ZERO);
        var estimates=a.rows("SELECT i.id AS invoice_id,r.id AS service_request_id,b.net_labour_amount,v.percentage_rate FROM invoices i JOIN service_requests r ON r.id=i.service_request_id JOIN invoice_commission_bases b ON b.invoice_id=i.id JOIN technician_assignments t ON t.service_request_id=r.id AND t.technician_id=? AND t.status='COMPLETED' JOIN commission_rule_versions v ON v.service_type=r.service_type AND v.basis_type='NET_LABOUR_PERCENT' AND v.status='APPROVED' AND v.effective_from<=CAST(r.completed_at AS DATE) AND (v.effective_until IS NULL OR v.effective_until>=CAST(r.completed_at AS DATE)) WHERE i.status='ISSUED' AND r.status='COMPLETED' AND (SELECT COUNT(*) FROM technician_assignments other WHERE other.service_request_id=r.id AND other.status='COMPLETED')=1 AND NOT EXISTS(SELECT 1 FROM technician_commissions c WHERE c.invoice_id=i.id AND c.technician_profile_id=?)",tech,tech);
        for(var estimate:estimates){BigDecimal value=((BigDecimal)estimate.get("netLabourAmount")).multiply((BigDecimal)estimate.get("percentageRate")).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);estimate.put("estimatedAmount",value);summary.merge("pendingCollection",value,BigDecimal::add);}
        for(var c:entries){BigDecimal amount=(BigDecimal)c.get("calculatedAmount");summary.merge("earned",amount,BigDecimal::add);summary.merge("adjustments",(BigDecimal)c.get("adjustments"),BigDecimal::add);String key=switch((String)c.get("status")){case "READY"->c.get("payoutReference")==null?"ready":"pendingApproval";case "PAID"->"paid";case "HELD","ADJUSTED"->"held";default->"pendingApproval";};summary.merge(key,amount.add((BigDecimal)c.get("adjustments")),BigDecimal::add);c.put("adjustmentHistory",a.rows("SELECT adjustment_amount,reason,created_at FROM commission_adjustments WHERE commission_id=? ORDER BY id",c.get("id")));}
        var batches=a.rows("SELECT b.* FROM technician_payout_batches b WHERE EXISTS(SELECT 1 FROM technician_payout_items i JOIN technician_commissions c ON c.id=i.commission_id WHERE i.batch_id=b.id AND c.technician_profile_id=?) ORDER BY b.id DESC",tech);
        for(var b:batches){b.put("jobs",a.rows("SELECT c.service_request_id,i.amount FROM technician_payout_items i JOIN technician_commissions c ON c.id=i.commission_id WHERE i.batch_id=? AND c.technician_profile_id=?",b.get("id"),tech));b.put("offsets",a.rows("SELECT o.amount,x.reason,c.service_request_id FROM technician_payout_adjustments o JOIN commission_adjustments x ON x.id=o.adjustment_id JOIN technician_commissions c ON c.id=x.commission_id WHERE o.batch_id=? AND c.technician_profile_id=?",b.get("id"),tech));}
        summary.put("paid",batches.stream().filter(b->"SUCCEEDED".equals(b.get("status"))).map(b->(BigDecimal)b.get("totalAmount")).reduce(BigDecimal.ZERO,BigDecimal::add)); var workload=a.one("SELECT COUNT(DISTINCT CASE WHEN r.status IN ('ACCEPTED','ON_THE_WAY','REACHED_SITE','DIAGNOSIS') AND NOT EXISTS(SELECT 1 FROM service_inspections s WHERE s.service_request_id=r.id AND s.status IN ('SUBMITTED','APPROVED')) THEN r.id END) AS inspections_pending,COUNT(DISTINCT CASE WHEN r.status='WAITING_FOR_PARTS' THEN r.id END) AS waiting_for_parts,COUNT(DISTINCT CASE WHEN EXISTS(SELECT 1 FROM service_work_approvals w WHERE w.service_request_id=r.id AND w.status IN ('PENDING_ADMIN','AWAITING_CUSTOMER')) THEN r.id END) AS quotations_pending FROM service_requests r JOIN technician_assignments t ON t.service_request_id=r.id WHERE t.technician_id=? AND t.status='ACCEPTED' AND r.status NOT IN ('COMPLETED','CANCELLED')",tech);return Map.of("summary",summary,"commissions",entries,"estimates",estimates,"payouts",batches,"workload",workload,"currency","INR");
    }
    List<Map<String,Object>> expenses(){if(a.actor().getRole()==com.valor.auth.Role.TECHNICIAN)return a.rows("SELECT * FROM technician_expense_claims WHERE technician_profile_id=? ORDER BY id DESC",a.technician());a.admin();return a.rows("SELECT * FROM technician_expense_claims ORDER BY id DESC LIMIT 200");}
    List<Map<String,Object>> expense(Expense in){
        a.job(in.serviceRequestId(),false);long tech=a.technician();
        if(a.count("SELECT COUNT(*) FROM finance_evidence WHERE id=? AND service_request_id=? AND uploaded_by_user_id=? AND payout_batch_id IS NULL",in.attachmentId(),in.serviceRequestId(),a.actor().getId())==0)throw new CommerceException(400,"Upload a private purchase receipt for this job first");
        if(a.count("SELECT COUNT(*) FROM technician_assignments WHERE service_request_id=? AND technician_id=? AND status IN ('ACCEPTED','COMPLETED')",in.serviceRequestId(),tech)==0)throw new CommerceException(403,"Accepted assignment is required");
        if(in.purchaseDate()==null||in.purchaseDate().isAfter(LocalDate.now()))throw new CommerceException(400,"Invalid purchase date");
        String key=FinanceAccess.text(in.idempotencyKey(),80);if(a.count("SELECT COUNT(*) FROM technician_expense_claims WHERE technician_profile_id=? AND idempotency_key=?",tech,key)>0)throw new CommerceException(409,"Expense submission already exists");
        String storage=(String)a.one("SELECT storage_key FROM finance_evidence WHERE id=?",in.attachmentId()).get("storageKey");
        a.db.update("INSERT INTO technician_expense_claims(service_request_id,technician_profile_id,category,description,amount,purchase_date,receipt_storage_key,reason,idempotency_key) VALUES(?,?,?,?,?,?,?,?,?)",in.serviceRequestId(),tech,FinanceAccess.text(in.category(),40),FinanceAccess.text(in.description(),500),FinanceAccess.money(in.amount(),false),in.purchaseDate(),storage,FinanceAccess.text(in.reason(),1000),key);a.event(in.serviceRequestId(),"EXPENSE_SUBMITTED",in.description());return expenses();
    }
    List<Map<String,Object>> reviewExpense(long id,ExpenseReview in){
        FinanceAccess.text(in.action(),40);
        a.admin();var claim=a.one("SELECT * FROM technician_expense_claims WHERE id=? FOR UPDATE",id);String reason=FinanceAccess.text(in.reason(),1000),status=(String)claim.get("status"),next;
        switch(in.action()){case "APPROVE","REJECT"->{if(!List.of("SUBMITTED","UNDER_REVIEW").contains(status))throw new CommerceException(409,"Expense was already reviewed");next=in.action().equals("APPROVE")?"APPROVED":"REJECTED";}
            case "REIMBURSE"->{if(!status.equals("APPROVED"))throw new CommerceException(409,"Expense must be approved before reimbursement");FinanceAccess.text(in.reimbursementReference(),120);next="REIMBURSED";}
            default->throw new CommerceException(400,"Invalid expense action");}
        a.db.update("UPDATE technician_expense_claims SET status=?,reviewed_by_user_id=?,reviewed_at=CURRENT_TIMESTAMP,review_reason=?,reimbursement_reference=? WHERE id=?",next,a.actor().getId(),reason,in.reimbursementReference(),id);a.event(((Number)claim.get("serviceRequestId")).longValue(),"EXPENSE_"+next,reason);return expenses();
    }
    Map<String,Object> report(){return report(Map.of());}
    Map<String,Object> report(Map<String,String> filters){return new FinanceReporting(a).report(filters);}
}
