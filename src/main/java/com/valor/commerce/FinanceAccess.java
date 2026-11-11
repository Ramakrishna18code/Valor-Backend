package com.valor.commerce;

import com.valor.auth.*;
import java.math.BigDecimal;
import java.sql.Statement;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class FinanceAccess {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private AuditService audit;
    final JdbcTemplate db;
    final AssetIdentityAccess identities;
    public FinanceAccess(JdbcTemplate db, AssetIdentityAccess identities) { this.db=db; this.identities=identities; }
    User actor() { return identities.actor(); }
    void admin() { identities.requireAdmin(); }
    long technician() {
        User actor=actor();
        if(actor.getRole()!=Role.TECHNICIAN) throw new AccessDeniedException("Technician access required");
        var ids=db.queryForList("SELECT id FROM technician_profiles WHERE user_id=? AND is_active=TRUE",Long.class,actor.getId());
        if(ids.isEmpty()) throw new AccessDeniedException("Technician profile unavailable");
        return ids.get(0);
    }
    Map<String,Object> job(long id, boolean write) {
        var jobs=rows("SELECT r.*,c.user_id AS customer_user_id FROM service_requests r JOIN customer_profiles c ON c.id=r.customer_id WHERE r.id=?",id);
        if(jobs.isEmpty()) throw new CommerceException(404,"Service request not found");
        var job=jobs.get(0); User actor=actor();
        if(actor.getRole()==Role.ADMIN || actor.getRole()==Role.SUPER_ADMIN) return job;
        if(actor.getRole()==Role.CUSTOMER && !write && ((Number)job.get("customerUserId")).longValue()==actor.getId()) {
            identities.requireActiveCustomerUser(actor); return job;
        }
        if(actor.getRole()==Role.TECHNICIAN) {
            long tech=technician();
            String statuses=write ? "('ACCEPTED')" : "('ASSIGNED','ACCEPTED','COMPLETED')";
            if(count("SELECT COUNT(*) FROM technician_assignments WHERE service_request_id=? AND technician_id=? AND status IN "+statuses,id,tech)>0) {
                if(write && List.of("COMPLETED","CANCELLED").contains(job.get("status"))) throw new CommerceException(409,"Job is already closed");
                return job;
            }
        }
        throw new AccessDeniedException("Assigned job access required");
    }
    List<Map<String,Object>> rows(String sql,Object...args) {
        return db.query(sql,(rs,n)->{var row=new LinkedHashMap<String,Object>(); var meta=rs.getMetaData();
            for(int i=1;i<=meta.getColumnCount();i++) {String key=meta.getColumnLabel(i).toLowerCase(Locale.ROOT);var name=new StringBuilder();boolean upper=false;
                for(char c:key.toCharArray()){if(c=='_'){upper=true;continue;}name.append(upper?Character.toUpperCase(c):c);upper=false;}
                Object value=rs.getObject(i); if(value instanceof java.sql.Timestamp time)value=time.toLocalDateTime(); if(value instanceof java.sql.Date date)value=date.toLocalDate();
                row.put(name.toString(),value); }return row;},args);
    }
    Map<String,Object> one(String sql,Object...args) {var rows=rows(sql,args);if(rows.isEmpty())throw new CommerceException(404,"Record not found");return rows.get(0);}
    long count(String sql,Object...args){return db.queryForObject(sql,Long.class,args);}
    BigDecimal sum(String sql,Object...args){return db.queryForObject(sql,BigDecimal.class,args);}
    long insert(String sql,Object...args) {
        var key=new GeneratedKeyHolder(); db.update(connection->{var statement=connection.prepareStatement(sql,new String[]{"id"});
            for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement;},key);return key.getKey().longValue();
    }
    void event(long job,String type,String reason){db.update("INSERT INTO service_finance_events(service_request_id,event_type,actor_user_id,reason) VALUES(?,?,?,?)",job,type,actor().getId(),text(reason,2000));audit(type,"SERVICE_REQUEST",job,reason);}
    void audit(String action,String type,long id,String reason){if(audit!=null)audit.record(action,type,id,reason);}
    static String text(String value,int max){if(value==null||value.isBlank()||value.trim().length()>max)throw new CommerceException(400,"Required text is missing or too long");return value.trim();}
    static boolean oneOf(String value,String...allowed){return value!=null&&Arrays.asList(allowed).contains(value);}
    static BigDecimal money(BigDecimal value,boolean zero){if(value==null||value.scale()>2||value.signum()<(zero?0:1))throw new CommerceException(400,"Invalid monetary amount");if(value.compareTo(new BigDecimal("9999999999.99"))>0)throw new CommerceException(400,"Monetary amount exceeds the supported limit");return value.setScale(2);}
    long evidence(long job,long attachment){if(count("SELECT COUNT(*) FROM service_request_attachments WHERE id=? AND service_request_id=?",attachment,job)==0)throw new CommerceException(400,"Evidence must be an attachment on this service request");return attachment;}
    public void requireWorkApproved(long job) {
        if(count("SELECT COUNT(*) FROM service_inspections WHERE service_request_id=? AND safety_status IN ('UNSAFE','RESTRICTED')",job)>0)
            throw new CommerceException(409,"Safety clearance is required before repair or completion");
        if(count("SELECT COUNT(*) FROM service_work_approvals WHERE service_request_id=? AND status IN ('PENDING_ADMIN','AWAITING_CUSTOMER')",job)>0)
            throw new CommerceException(409,"Additional work is awaiting approval");
        if(count("SELECT COUNT(*) FROM service_finance_holds WHERE service_request_id=? AND resolved_at IS NULL",job)>0)
            throw new CommerceException(409,"An unresolved service or payment dispute blocks this action");
    }
    public void requireAssignedJob(long job) { job(job,true); }
    void requirePaymentStageDue(long invoice) {
        var stages=rows("SELECT m.due_condition,m.due_date,r.status AS job_status FROM service_payment_milestones m JOIN service_requests r ON r.id=m.service_request_id WHERE m.invoice_id=?",invoice);
        if(!stages.isEmpty()&&!stageDue(stages.get(0)))throw new CommerceException(409,"This payment stage is upcoming; its agreed due condition has not been met");
    }
    static boolean stageDue(Map<String,Object> stage) {
        return switch(String.valueOf(stage.get("dueCondition"))) {
            case "ON_COMPLETION" -> "COMPLETED".equals(stage.get("jobStatus"));
            case "SCHEDULED" -> stage.get("dueDate") instanceof java.time.LocalDate date && !date.isAfter(java.time.LocalDate.now());
            default -> true;
        };
    }
    public void requireAttachmentDeletable(long job,long attachment) {
        // Inspection submission takes the same job lock before linking evidence.
        one("SELECT id FROM service_requests WHERE id=? FOR UPDATE",job);
        if(count("SELECT COUNT(*) FROM service_inspection_evidence WHERE attachment_id=?",attachment)>0)
            throw new CommerceException(409,"This attachment is retained as inspection evidence");
    }
}
