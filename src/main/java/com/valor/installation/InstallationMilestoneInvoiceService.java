package com.valor.installation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.valor.auth.AssetIdentityAccess;
import com.valor.auth.AuditService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static com.valor.installation.InstallationMilestoneInvoiceDtos.*;

@Service @Transactional
class InstallationMilestoneInvoiceService {
    private final InstallationMilestoneRepository milestones;
    private final InstallationMilestoneEligibilityService eligibility;
    private final AssetIdentityAccess access;
    private final EntityManager em;
    private final ObjectMapper json;
    private final AuditService audit;
    InstallationMilestoneInvoiceService(InstallationMilestoneRepository m, InstallationMilestoneEligibilityService e, AssetIdentityAccess a, EntityManager em, ObjectMapper j, AuditService audit){milestones=m;eligibility=e;access=a;this.em=em;json=j;this.audit=audit;}

    View generate(Long milestoneId){
        access.requireAdmin();
        var m=milestones.lockById(milestoneId).orElseThrow(()->new IllegalArgumentException("Milestone not found"));
        if(m.getInvoiceId()!=null)return existing(m);
        var schedule=m.getSchedule();
        if(schedule==null)throw blocked("Milestone has no approved schedule");
        JsonNode definition=definition(schedule,m.getMilestoneCode());
        String trigger=definition.path("trigger").asText(null);
        if(!"INSTALLATION_COMPLETED".equals(trigger)||m.getRequest().getStatus()!=InstallationStatus.COMPLETED)throw blocked("Billing trigger cannot be verified from authoritative installation state");
        eligibility.assertEligible(m,schedule,trigger);
        BigDecimal amount=decimal(definition,"amount"); BigDecimal tax=decimal(definition,"taxAmount");
        if(amount==null||tax==null||tax.signum()<0||amount.signum()<=0)throw blocked("Approved amount and tax terms are required");
        if(m.getAmount().compareTo(amount)!=0)throw blocked("Milestone amount differs from approved schedule");
        BigDecimal total=amount.add(tax);
        Number existingNumber=(Number)em.createNativeQuery("select id from invoices where installation_milestone_id=? and status not in ('VOID','CANCELLED') order by id desc limit 1").setParameter(1,milestoneId).getResultStream().findFirst().orElse(null);
        Long existing=existingNumber==null?null:existingNumber.longValue();
        if(existing!=null){m.setInvoiceId(existing);m.setStatus(InstallationMilestoneStatus.INVOICED);return existing(m);}
        Long customerId=m.getRequest().getCustomer().getId();
        em.createNativeQuery("insert into invoices(invoice_number,customer_id,created_by_user_id,description,subtotal,tax_amount,total_amount,currency,status,issued_date,installation_milestone_id) values (?,?,?,?,?,?,?,?,?,?,?)")
          .setParameter(1,"INST-"+UUID.randomUUID().toString().substring(0,12).toUpperCase()).setParameter(2,customerId).setParameter(3,access.actor().getId()).setParameter(4,"Installation milestone "+m.getMilestoneCode()).setParameter(5,amount).setParameter(6,tax).setParameter(7,total).setParameter(8,schedule.getCurrency()).setParameter(9,"ISSUED").setParameter(10,LocalDate.now()).setParameter(11,milestoneId).executeUpdate();
        Long invoiceId=((Number)em.createNativeQuery("select id from invoices where installation_milestone_id=? order by id desc limit 1").setParameter(1,milestoneId).getSingleResult()).longValue();
        m.setInvoiceId(invoiceId);m.setStatus(InstallationMilestoneStatus.INVOICED);milestones.saveAndFlush(m);audit.record("INSTALLATION_MILESTONE_INVOICE_CREATE","INSTALLATION_MILESTONE",milestoneId,"Milestone invoice created");return new View(milestoneId,invoiceId,m.getRequest().getId(),amount,tax,total,schedule.getCurrency(),"ISSUED",LocalDate.now());
    }
    private View existing(InstallationMilestone m){return new View(m.getId(),m.getInvoiceId(),m.getRequest().getId(),m.getAmount(),null,null,m.getCurrency(),"ISSUED",null);}
    private JsonNode definition(InstallationSchedule s,String code){try{JsonNode a=json.readTree(s.getMilestoneDefinitions());for(JsonNode n:a)if(code.equals(n.path("code").asText()))return n;throw blocked("Milestone is not defined by approved schedule");}catch(InstallationMilestoneEligibilityService.InstallationEligibilityException e){throw e;}catch(Exception e){throw blocked("Schedule terms are invalid");}}
    private BigDecimal decimal(JsonNode n,String field){return n.has(field)&&n.get(field).isNumber()?n.get(field).decimalValue():null;}
    private static InstallationMilestoneEligibilityService.InstallationEligibilityException blocked(String s){return new InstallationMilestoneEligibilityService.InstallationEligibilityException(s);}
}
