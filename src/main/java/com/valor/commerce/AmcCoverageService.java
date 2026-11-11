package com.valor.commerce;

import com.valor.auth.Role;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AmcCoverageService {
    public record Entitlement(String serviceType,String scope,boolean covered,LocalDate effectiveFrom,LocalDate effectiveUntil,Integer includedVisitLimit,String note) {
        public Entitlement(String serviceType,String scope,boolean covered,LocalDate effectiveFrom,LocalDate effectiveUntil,String note){this(serviceType,scope,covered,effectiveFrom,effectiveUntil,null,note);}
    }
    public record Decision(String scope,LocalDate serviceDate) {}
    private final FinanceAccess a;
    AmcCoverageService(FinanceAccess a){this.a=a;}

    public List<Map<String,Object>> entitlements(long contractId){
        a.admin();a.one("SELECT id FROM amc_contracts WHERE id=?",contractId);
        return a.rows("SELECT id,service_type,scope,covered,included_visit_limit,effective_from,effective_until,note,updated_at FROM amc_service_entitlements WHERE contract_id=? ORDER BY service_type,scope",contractId);
    }
    public Map<String,Object> saveEntitlement(long contractId,Entitlement input){
        a.admin();
        if(input==null||!FinanceAccess.oneOf(input.serviceType(),"ROUTINE_MAINTENANCE","BREAKDOWN","EMERGENCY","INSPECTION","INSTALLATION","MODERNIZATION")||!FinanceAccess.oneOf(input.scope(),"VISIT","LABOUR","PARTS")||input.effectiveFrom()==null||input.effectiveUntil()==null||input.effectiveUntil().isBefore(input.effectiveFrom()))throw new CommerceException(400,"Choose a service, coverage scope and valid dates");
        var contract=a.one("SELECT start_date,end_date FROM amc_contracts WHERE id=? FOR UPDATE",contractId);
        if(input.effectiveFrom().isBefore((LocalDate)contract.get("startDate"))||input.effectiveUntil().isAfter((LocalDate)contract.get("endDate")))throw new CommerceException(400,"Coverage dates must be within the maintenance plan");
        if(input.includedVisitLimit()!=null&&(!"VISIT".equals(input.scope())||!input.covered()||input.includedVisitLimit()<1))throw new CommerceException(400,"An included visit limit must be a positive number on a covered visit");
        String note=input.note()==null?null:FinanceAccess.text(input.note(),500);
        var prior=a.rows("SELECT id FROM amc_service_entitlements WHERE contract_id=? AND service_type=? AND scope=? FOR UPDATE",contractId,input.serviceType(),input.scope());
        long id;
        if(prior.isEmpty())id=a.insert("INSERT INTO amc_service_entitlements(contract_id,service_type,scope,covered,included_visit_limit,effective_from,effective_until,note,updated_by_user_id) VALUES(?,?,?,?,?,?,?,?,?)",contractId,input.serviceType(),input.scope(),input.covered(),input.includedVisitLimit(),input.effectiveFrom(),input.effectiveUntil(),note,a.actor().getId());
        else {id=((Number)prior.get(0).get("id")).longValue();a.db.update("UPDATE amc_service_entitlements SET covered=?,included_visit_limit=?,effective_from=?,effective_until=?,note=?,updated_by_user_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",input.covered(),input.includedVisitLimit(),input.effectiveFrom(),input.effectiveUntil(),note,a.actor().getId(),id);}
        a.audit("AMC_ENTITLEMENT_SAVED","AMC_CONTRACT",contractId,input.serviceType()+" "+input.scope());
        return a.one("SELECT id,service_type,scope,covered,included_visit_limit,effective_from,effective_until,note,updated_at FROM amc_service_entitlements WHERE id=?",id);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> preview(long liftId,String serviceType,String scope,LocalDate date){
        if(date==null||!FinanceAccess.oneOf(serviceType,"ROUTINE_MAINTENANCE","BREAKDOWN","EMERGENCY","INSPECTION","INSTALLATION","MODERNIZATION")||!FinanceAccess.oneOf(scope,"VISIT","LABOUR","PARTS"))throw new CommerceException(400,"Choose a service, coverage scope and date");
        var lift=a.one("SELECT l.id,b.customer_id,c.user_id AS customer_user_id FROM lifts l JOIN buildings b ON b.id=l.building_id JOIN customer_profiles c ON c.id=b.customer_id WHERE l.id=?",liftId);
        if(a.actor().getRole()==Role.CUSTOMER&&((Number)lift.get("customerUserId")).longValue()!=a.actor().getId())throw new org.springframework.security.access.AccessDeniedException("Lift access required");
        if(a.actor().getRole()==Role.CUSTOMER)a.identities.requireActiveCustomerUser(a.actor());
        if(!List.of(Role.CUSTOMER,Role.ADMIN,Role.SUPER_ADMIN).contains(a.actor().getRole()))throw new org.springframework.security.access.AccessDeniedException("Coverage access required");
        return decide(liftId,serviceType,scope,date,null);
    }
    private Map<String,Object> decide(long liftId,String serviceType,String scope,LocalDate date,Long currentRequestId){
        var contracts=a.rows("SELECT id FROM amc_contracts WHERE lift_id=? AND status='ACTIVE' AND start_date<=? AND end_date>=? ORDER BY id DESC LIMIT 1",liftId,date,date);
        if(contracts.isEmpty())return decision(null,null,serviceType,scope,date,"UNCONFIRMED","No active maintenance plan covers this date");
        long contract=((Number)contracts.get(0).get("id")).longValue();
        var terms=a.rows("SELECT id,covered,included_visit_limit,effective_from,effective_until FROM amc_service_entitlements WHERE contract_id=? AND service_type=? AND scope=? AND effective_from<=? AND effective_until>=?",contract,serviceType,scope,date,date);
        if(terms.isEmpty())return decision(contract,null,serviceType,scope,date,"UNCONFIRMED","This service and scope have not been confirmed in the plan");
        var term=terms.get(0);long entitlement=((Number)term.get("id")).longValue();boolean covered=Boolean.TRUE.equals(term.get("covered"))||term.get("covered") instanceof Number n&&n.intValue()!=0;
        if(covered&&"VISIT".equals(scope)&&term.get("includedVisitLimit")!=null){
            long used=a.count("SELECT COUNT(*) FROM service_request_coverage_decisions d JOIN service_requests r ON r.id=d.service_request_id WHERE d.contract_id=? AND d.service_type=? AND d.scope='VISIT' AND d.outcome='COVERED' AND d.service_date>=? AND d.service_date<=? AND r.status<>'CANCELLED' AND d.service_request_id<>? AND d.version_number=(SELECT MAX(v.version_number) FROM service_request_coverage_decisions v WHERE v.service_request_id=d.service_request_id AND v.scope='VISIT')",contract,serviceType,term.get("effectiveFrom"),term.get("effectiveUntil"),currentRequestId==null?-1:currentRequestId);
            if(used>=((Number)term.get("includedVisitLimit")).longValue())return decision(contract,entitlement,serviceType,scope,date,"EXCLUDED","Included visits have been used; review the service price before work begins");
        }
        return decision(contract,entitlement,serviceType,scope,date,covered?"COVERED":"EXCLUDED",covered?"Included in this maintenance plan":"Not included in this maintenance plan");
    }
    private static Map<String,Object> decision(Long contract,Long entitlement,String service,String scope,LocalDate date,String outcome,String reason){
        var result=new java.util.LinkedHashMap<String,Object>();result.put("contractId",contract);result.put("entitlementId",entitlement);result.put("serviceType",service);result.put("scope",scope);result.put("serviceDate",date);result.put("outcome",outcome);result.put("reason",reason);return result;
    }
    public Map<String,Object> capture(long requestId,String scope,LocalDate date){
        var request=a.one("SELECT lift_id,service_type,preferred_visit_date,created_at FROM service_requests WHERE id=? FOR UPDATE",requestId);
        if(request.get("liftId")==null)throw new CommerceException(400,"A lift is needed to check maintenance coverage");
        if(date==null)date=request.get("preferredVisitDate") instanceof LocalDate d?d:((java.time.LocalDateTime)request.get("createdAt")).toLocalDate();
        a.rows("SELECT id FROM amc_contracts WHERE lift_id=? AND status='ACTIVE' AND start_date<=? AND end_date>=? ORDER BY id DESC FOR UPDATE",request.get("liftId"),date,date);
        var result=decide(((Number)request.get("liftId")).longValue(),String.valueOf(request.get("serviceType")),scope,date,requestId);
        int version=a.db.queryForObject("SELECT COALESCE(MAX(version_number),0)+1 FROM service_request_coverage_decisions WHERE service_request_id=? AND scope=?",Integer.class,requestId,scope);
        a.db.update("INSERT INTO service_request_coverage_decisions(service_request_id,version_number,contract_id,entitlement_id,service_type,scope,service_date,outcome,reason) VALUES(?,?,?,?,?,?,?,?,?)",requestId,version,result.get("contractId"),result.get("entitlementId"),result.get("serviceType"),scope,date,result.get("outcome"),result.get("reason"));
        return result;
    }
    public Map<String,Object> captureAdmin(long requestId,Decision input){a.admin();if(input==null||!FinanceAccess.oneOf(input.scope(),"VISIT","LABOUR","PARTS"))throw new CommerceException(400,"Choose visit, labour or parts coverage");return capture(requestId,input.scope(),input.serviceDate());}
    @Transactional(readOnly=true)
    public List<Map<String,Object>> decisions(long requestId){a.job(requestId,false);return a.rows("SELECT service_type,scope,service_date,outcome,reason,decided_at FROM service_request_coverage_decisions d WHERE d.service_request_id=? AND d.version_number=(SELECT MAX(v.version_number) FROM service_request_coverage_decisions v WHERE v.service_request_id=d.service_request_id AND v.scope=d.scope) ORDER BY d.id",requestId);}

    /** A recorded decision owns coverage; plan presence can be shown before review. */
    public record RequestClassification(String amcCategory, String visitCoverage) {}
    @Transactional(readOnly=true)
    public RequestClassification requestClassification(long requestId) {
        var request = a.job(requestId, false);
        if (request.get("liftId") == null) return new RequestClassification("NON_AMC", "UNCONFIRMED");
        var decisions = a.rows("SELECT contract_id,outcome FROM service_request_coverage_decisions WHERE service_request_id=? AND scope='VISIT' ORDER BY version_number DESC,id DESC LIMIT 1", requestId);
        if (decisions.isEmpty()) {
            LocalDate date = request.get("preferredVisitDate") instanceof LocalDate preferred ? preferred
                    : request.get("createdAt") instanceof java.time.LocalDateTime created ? created.toLocalDate() : null;
            if (date == null) return new RequestClassification("UNKNOWN", "UNCONFIRMED");
            var contracts = a.rows("SELECT id FROM amc_contracts WHERE lift_id=? AND status='ACTIVE' AND start_date<=? AND end_date>=? ORDER BY id DESC LIMIT 1", request.get("liftId"), date, date);
            return new RequestClassification(contracts.isEmpty() ? "NON_AMC" : "AMC", "UNCONFIRMED");
        }
        var latest = decisions.get(0);
        return new RequestClassification(latest.get("contractId") == null ? "NON_AMC" : "AMC", String.valueOf(latest.get("outcome")));
    }
}
