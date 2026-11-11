package com.valor.commerce;

import com.valor.auth.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:finance_workflows_v67;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
@Transactional
class FinanceWorkflowIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties){
        String url=System.getenv("FINANCE_TEST_JDBC_URL");if(url==null||url.isBlank())return;
        if(!url.matches("jdbc:mysql://localhost:3306/valor_world_finance_[A-Za-z0-9_]+(?:\\?.*)?"))throw new IllegalStateException("Finance tests require an isolated local finance database");
        properties.add("spring.datasource.url",()->url);properties.add("spring.datasource.username",()->System.getenv("FINANCE_TEST_DB_USERNAME"));properties.add("spring.datasource.password",()->System.getenv("FINANCE_TEST_DB_PASSWORD"));properties.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");properties.add("spring.jpa.database-platform",()->"org.hibernate.dialect.MySQLDialect");
    }
    @Autowired FinanceAccess a;
    @Autowired ServiceFinanceWorkflow jobs;
    @Autowired AmcCoverageService coverage;
    @Autowired ServiceTeamService team;
    @Autowired TechnicianFinanceService finance;
    @Autowired FinanceEvidenceService evidence;
    @Autowired ServiceQuotationService quotations;
    @MockitoBean AssetIdentityAccess identities;
    @MockitoBean AssetDocumentStorage storage;
    private final AtomicReference<User> actor=new AtomicReference<>();
    private User admin,checker,customer,technician;
    private long customerProfile,tech,job,assignment,attachment;
    private BigDecimal amount(String value){return new BigDecimal(value);}
    private long id(Map<String,Object> row){return ((Number)row.get("id")).longValue();}
    @BeforeEach void setup() throws Exception {
        when(identities.actor()).thenAnswer(i->actor.get());
        doAnswer(i->{if(!List.of(Role.ADMIN,Role.SUPER_ADMIN).contains(actor.get().getRole()))throw new org.springframework.security.access.AccessDeniedException("Admin required");return null;}).when(identities).requireAdmin();
        when(storage.store(anyString(),any())).thenAnswer(i->new StoredAssetDocument(UUID.randomUUID()+i.getArgument(0).toString()));
        admin=user(Role.ADMIN);checker=user(Role.ADMIN);customer=user(Role.CUSTOMER);technician=user(Role.TECHNICIAN);actor.set(admin);
        customerProfile=a.insert("INSERT INTO customer_profiles(user_id,full_name) VALUES(?,?)",customer.getId(),"Finance customer");
        tech=a.insert("INSERT INTO technician_profiles(user_id,employee_id) VALUES(?,?)",technician.getId(),UUID.randomUUID().toString());
        long building=a.insert("INSERT INTO buildings(customer_id,building_name) VALUES(?,'Finance building')",customerProfile);
        long lift=a.insert("INSERT INTO lifts(building_id,name) VALUES(?,'Finance lift')",building);
        job=a.insert("INSERT INTO service_requests(customer_id,lift_id,service_id,title,description,status,service_type) VALUES(?,?,?,?,?,?,?)",customerProfile,lift,UUID.randomUUID().toString(),"Repair","Fault","DIAGNOSIS","BREAKDOWN");
        assignment=a.insert("INSERT INTO technician_assignments(service_request_id,technician_id,assigned_by_user_id,status,accepted_at) VALUES(?,?,?,'ACCEPTED',CURRENT_TIMESTAMP)",job,tech,admin.getId());
        attachment=a.insert("INSERT INTO service_request_attachments(service_request_id,uploaded_by_user_id,original_filename,content_type,file_size,storage_key) VALUES(?,?,?,'image/jpeg',20,?)",job,technician.getId(),"inspection.jpg",UUID.randomUUID().toString());
    }
    private User user(Role role){long id=a.insert("INSERT INTO users(email,role) VALUES(?,?)",UUID.randomUUID()+"@finance.test",role.name());User user=new User();ReflectionTestUtils.setField(user,"id",id);user.setRole(role);return user;}
    private MockMultipartFile pdf(){return new MockMultipartFile("file","evidence.pdf","application/pdf","%PDF-1.4 test evidence bytes".getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
    private void complete(){a.db.update("UPDATE service_requests SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE id=?",job);a.db.update("UPDATE technician_assignments SET status='COMPLETED' WHERE id=?",assignment);a.db.update("INSERT INTO service_reports(service_request_id,assignment_id,reported_by_user_id,diagnosis,work_performed,testing_result) VALUES(?,?,?,'Diagnosis','Repair','Passed')",job,assignment,technician.getId());}
    private long paidInvoice(){long invoice=a.insert("INSERT INTO invoices(customer_id,service_request_id,created_by_user_id,description,subtotal,tax_amount,total_amount,currency,status,issued_date,created_at,updated_at) VALUES(?,?,?,'Repair',1000,0,1000,'INR','PAID',CURRENT_DATE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",customerProfile,job,admin.getId());a.db.update("INSERT INTO payment_records(customer_id,service_request_id,invoice_id,created_by_user_id,amount,currency,purpose,status,created_at,updated_at) VALUES(?,?,?,?,1000,'INR','INVOICE','SUCCEEDED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",customerProfile,job,invoice,admin.getId());return invoice;}
    private long readyCommission(long invoice){var existing=a.rows("SELECT * FROM commission_rule_versions WHERE service_type='BREAKDOWN' AND status='APPROVED' AND basis_type='NET_LABOUR_PERCENT' AND percentage_rate=10 AND effective_from<=CURRENT_DATE AND (effective_until IS NULL OR effective_until>=CURRENT_DATE)");var rule=existing.isEmpty()?finance.rule(new TechnicianFinanceService.Rule("BREAKDOWN","NET_LABOUR_PERCENT",amount("10"),null,LocalDate.now().minusDays(1),null)):existing.get(0);if(existing.isEmpty())finance.approveRule(id(rule));finance.basis(new TechnicianFinanceService.Basis(invoice,amount("800.00"),"Approved labour excludes parts and tax"));var c=finance.accrue(new TechnicianFinanceService.Accrue(tech,job,invoice,id(rule),"repair-1",amount("800.00"),"Completion and payment verified"));finance.decideCommission(id(c),new TechnicianFinanceService.Decision(true,"Checked completion evidence"));return id(c);}

    @Test void financeSummarySubtractsConfirmedPartialPaymentsFromAmountDue(){
        long invoice=a.insert("INSERT INTO invoices(customer_id,service_request_id,created_by_user_id,description,subtotal,tax_amount,total_amount,currency,status,issued_date,created_at,updated_at) VALUES(?,?,?,'Repair',1000,0,1000,'INR','ISSUED',CURRENT_DATE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",customerProfile,job,admin.getId());
        a.db.update("INSERT INTO payment_records(customer_id,service_request_id,invoice_id,created_by_user_id,amount,currency,purpose,status,created_at,updated_at) VALUES(?,?,?,?,200,'INR','INVOICE','SUCCEEDED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",customerProfile,job,invoice,admin.getId());
        @SuppressWarnings("unchecked") var summary=(Map<String,Object>)jobs.view(job).get("billingSummary");
        assertEquals(0,amount("800.00").compareTo((BigDecimal)summary.get("amountDue")));
    }

    @Test void amcCoverageRequiresStructuredTermsAndRequestDecisionKeepsItsSnapshot(){
        long lift=((Number)a.one("SELECT lift_id FROM service_requests WHERE id=?",job).get("liftId")).longValue();
        long contract=a.insert("INSERT INTO amc_contracts(lift_id,amc_number,plan,start_date,end_date,status) VALUES(?,?,?, ?,?,'ACTIVE')",lift,UUID.randomUUID().toString(),"Standard",LocalDate.now().minusDays(10),LocalDate.now().plusDays(10));
        assertEquals("UNCONFIRMED",coverage.preview(lift,"BREAKDOWN","VISIT",LocalDate.now()).get("outcome"));
        coverage.saveEntitlement(contract,new AmcCoverageService.Entitlement("BREAKDOWN","VISIT",true,LocalDate.now().minusDays(10),LocalDate.now().plusDays(10),1,"One visit included"));
        coverage.saveEntitlement(contract,new AmcCoverageService.Entitlement("BREAKDOWN","PARTS",false,LocalDate.now().minusDays(10),LocalDate.now().plusDays(10),"Parts billed separately"));
        assertEquals("COVERED",coverage.capture(job,"VISIT",LocalDate.now()).get("outcome"));
        assertEquals("COVERED",coverage.capture(job,"VISIT",LocalDate.now().plusDays(1)).get("outcome"));
        assertEquals("COVERED",coverage.decisions(job).stream().filter(d->"VISIT".equals(d.get("scope"))).findFirst().orElseThrow().get("outcome"));
        assertEquals("EXCLUDED",coverage.preview(lift,"BREAKDOWN","VISIT",LocalDate.now()).get("outcome"));
        assertEquals("EXCLUDED",coverage.capture(job,"PARTS",LocalDate.now()).get("outcome"));
        coverage.saveEntitlement(contract,new AmcCoverageService.Entitlement("BREAKDOWN","VISIT",false,LocalDate.now().minusDays(10),LocalDate.now().plusDays(10),"New contract review"));
        actor.set(customer);
        assertEquals("COVERED",coverage.decisions(job).stream().filter(d->"VISIT".equals(d.get("scope"))).findFirst().orElseThrow().get("outcome"));
        assertEquals("EXCLUDED",coverage.preview(lift,"BREAKDOWN","VISIT",LocalDate.now()).get("outcome"));
    }

    @Test void coveredAmcWorkCannotBeReleasedAsAPaidQuotation(){
        long lift=((Number)a.one("SELECT lift_id FROM service_requests WHERE id=?",job).get("liftId")).longValue();
        long contract=a.insert("INSERT INTO amc_contracts(lift_id,amc_number,plan,start_date,end_date,status) VALUES(?,?,?, ?,?,'ACTIVE')",lift,UUID.randomUUID().toString(),"Standard",LocalDate.now().minusDays(1),LocalDate.now().plusDays(10));
        coverage.saveEntitlement(contract,new AmcCoverageService.Entitlement("BREAKDOWN","VISIT",true,LocalDate.now().minusDays(1),LocalDate.now().plusDays(10),"Visit included"));
        coverage.saveEntitlement(contract,new AmcCoverageService.Entitlement("BREAKDOWN","LABOUR",false,LocalDate.now().minusDays(1),LocalDate.now().plusDays(10),"Labour charged separately"));
        coverage.saveEntitlement(contract,new AmcCoverageService.Entitlement("BREAKDOWN","PARTS",false,LocalDate.now().minusDays(1),LocalDate.now().plusDays(10),"Parts charged separately"));
        coverage.capture(job,"VISIT",LocalDate.now());
        var visit=quotations.draft(job,new ServiceQuotationDtos.Create(null,null,null,BigDecimal.ZERO,"INR",null,List.of(new ServiceQuotationDtos.LineItemCreate("STANDARD_BREAKDOWN_CALLOUT",null,1,null,"VISIT"))));
        assertThrows(CommerceException.class,()->quotations.release(visit.id()));
        var missingScope=quotations.draft(job,new ServiceQuotationDtos.Create(null,null,null,BigDecimal.ZERO,"INR",null,List.of(new ServiceQuotationDtos.LineItemCreate("DOOR_ROLLER_REPLACEMENT",null,1,null))));
        assertThrows(CommerceException.class,()->quotations.release(missingScope.id()));
        var wrongScope=quotations.draft(job,new ServiceQuotationDtos.Create(null,null,null,BigDecimal.ZERO,"INR",null,List.of(new ServiceQuotationDtos.LineItemCreate("DOOR_ROLLER_REPLACEMENT",null,1,null,"VISIT"))));
        assertThrows(CommerceException.class,()->quotations.release(wrongScope.id()));
        var parts=quotations.draft(job,new ServiceQuotationDtos.Create(null,null,null,BigDecimal.ZERO,"INR",null,List.of(new ServiceQuotationDtos.LineItemCreate("DOOR_ROLLER_REPLACEMENT",null,1,null,"PARTS"))));
        assertEquals("RELEASED",quotations.release(parts.id()).releaseStatus());
        assertEquals("EXCLUDED",coverage.decisions(job).stream().filter(d->"PARTS".equals(d.get("scope"))).findFirst().orElseThrow().get("outcome"));
        a.db.update("UPDATE amc_contracts SET status='EXPIRED' WHERE id=?",contract);
        var later=quotations.draft(job,new ServiceQuotationDtos.Create(null,null,null,BigDecimal.ZERO,"INR",null,List.of(new ServiceQuotationDtos.LineItemCreate("DOOR_ROLLER_REPLACEMENT",null,1,null,"PARTS"))));
        assertEquals("RELEASED",quotations.release(later.id()).releaseStatus());
    }

    @Test void combinedRepairPriceIsHeldWhenLabourIsCoveredByAmc(){
        long lift=((Number)a.one("SELECT lift_id FROM service_requests WHERE id=?",job).get("liftId")).longValue();
        long contract=a.insert("INSERT INTO amc_contracts(lift_id,amc_number,plan,start_date,end_date,status) VALUES(?,?,?, ?,?,'ACTIVE')",lift,UUID.randomUUID().toString(),"Standard",LocalDate.now().minusDays(1),LocalDate.now().plusDays(10));
        coverage.saveEntitlement(contract,new AmcCoverageService.Entitlement("BREAKDOWN","VISIT",true,LocalDate.now().minusDays(1),LocalDate.now().plusDays(10),"Visit included"));
        coverage.saveEntitlement(contract,new AmcCoverageService.Entitlement("BREAKDOWN","LABOUR",true,LocalDate.now().minusDays(1),LocalDate.now().plusDays(10),"Labour included"));
        coverage.saveEntitlement(contract,new AmcCoverageService.Entitlement("BREAKDOWN","PARTS",false,LocalDate.now().minusDays(1),LocalDate.now().plusDays(10),"Parts charged separately"));
        coverage.capture(job,"VISIT",LocalDate.now());
        var quote=quotations.draft(job,new ServiceQuotationDtos.Create(null,null,null,BigDecimal.ZERO,"INR",null,List.of(new ServiceQuotationDtos.LineItemCreate("DOOR_ROLLER_REPLACEMENT",null,1,null,"PARTS"))));
        CommerceException error=assertThrows(CommerceException.class,()->quotations.release(quote.id()));
        assertTrue(error.getMessage().contains("labour and parts coverage"));
        assertEquals("COVERED",coverage.decisions(job).stream().filter(d->"LABOUR".equals(d.get("scope"))).findFirst().orElseThrow().get("outcome"));
    }

    @Test void approvedHelpersLogWorkAndAllowanceDoesNotReduceLeadCommission(){
        User helper=user(Role.TECHNICIAN);
        long helperProfile=a.insert("INSERT INTO technician_profiles(user_id,employee_id) VALUES(?,?)",helper.getId(),UUID.randomUUID().toString());
        actor.set(technician);
        long member=id(team.request(job,new ServiceTeamService.HelperRequest(helperProfile,"Heavy equipment needs a second person")));
        actor.set(admin);
        team.review(member,new ServiceTeamService.Review(true,"Approved for safe lifting","Ravi Kumar"));
        actor.set(helper);
        long log=id(team.log(member,new ServiceTeamService.WorkLog(LocalDate.now(),180,"Assisted with brake inspection")));
        actor.set(admin);
        team.reviewLog(log,new ServiceTeamService.LogReview(true));
        var proposed=team.allowance(member,new ServiceTeamService.Allowance("JOB",1,amount("1377.00"),"Manual approved job allowance"));
        complete();long invoice=paidInvoice();long commission=readyCommission(invoice);
        BigDecimal leadAmount=(BigDecimal)a.one("SELECT approved_amount FROM technician_commissions WHERE id=?",commission).get("approvedAmount");
        team.approveAllowance(id(proposed));
        assertThrows(CommerceException.class,()->team.payAllowance(id(proposed),new ServiceTeamService.Transfer(UUID.randomUUID().toString())));
        actor.set(checker);
        assertThrows(CommerceException.class,()->team.payAllowance(id(proposed),new ServiceTeamService.Transfer(UUID.randomUUID().toString())));
        long bankEvidence=id(evidence.uploadHelper(id(proposed),pdf()));
        team.payAllowance(id(proposed),new ServiceTeamService.Transfer(UUID.randomUUID().toString(),bankEvidence));
        assertEquals(bankEvidence,((Number)a.one("SELECT evidence_attachment_id FROM service_helper_allowances WHERE id=?",id(proposed)).get("evidenceAttachmentId")).longValue());
        actor.set(admin);
        assertEquals(0,leadAmount.compareTo((BigDecimal)a.one("SELECT approved_amount FROM technician_commissions WHERE id=?",commission).get("approvedAmount")));
        team.publicName(tech,new ServiceTeamService.PublicName("Anil Sharma"));
        actor.set(customer);
        var visible=team.view(job);
        assertEquals(1,((List<?>)visible.get("helpers")).size());
        assertEquals("Anil Sharma",((Map<?,?>)((List<?>)visible.get("lead")).get(0)).get("displayName"));
        assertFalse(visible.containsKey("allowances"));
    }

    @Test void helperPaymentWaitsForVerifiedCollectionAndRefundReview(){
        User helper=user(Role.TECHNICIAN);
        long helperProfile=a.insert("INSERT INTO technician_profiles(user_id,employee_id) VALUES(?,?)",helper.getId(),UUID.randomUUID().toString());
        actor.set(technician);long member=id(team.request(job,new ServiceTeamService.HelperRequest(helperProfile,"Second person needed")));
        actor.set(admin);team.review(member,new ServiceTeamService.Review(true,"Approved","Helper technician"));
        actor.set(helper);long log=id(team.log(member,new ServiceTeamService.WorkLog(LocalDate.now(),120,"Assisted with repair")));
        actor.set(admin);team.reviewLog(log,new ServiceTeamService.LogReview(true));
        long allowance=id(team.allowance(member,new ServiceTeamService.Allowance("JOB",1,amount("100.00"),"Approved manually")));
        complete();team.approveAllowance(allowance);
        actor.set(checker);long bankEvidence=id(evidence.uploadHelper(allowance,pdf()));
        var transfer=new ServiceTeamService.Transfer(UUID.randomUUID().toString(),bankEvidence);
        assertThrows(CommerceException.class,()->team.payAllowance(allowance,transfer));
        actor.set(admin);long invoice=paidInvoice();
        long payment=((Number)a.one("SELECT id FROM payment_records WHERE invoice_id=?",invoice).get("id")).longValue();
        long refund=a.insert("INSERT INTO payment_refunds(payment_id,requested_by_user_id,amount,currency,status,reason,created_at,updated_at) VALUES(?,?,100,'INR','REQUESTED','Customer review',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",payment,customer.getId());
        actor.set(checker);assertThrows(CommerceException.class,()->team.payAllowance(allowance,transfer));
        actor.set(admin);a.db.update("UPDATE payment_refunds SET status='REJECTED' WHERE id=?",refund);
        actor.set(checker);assertEquals("PAID",team.payAllowance(allowance,transfer).get("status"));
    }

    @Test void fixedVisitCommissionNeedsCoveredDecisionAndVerifiedPlanPayment(){
        long lift=((Number)a.one("SELECT lift_id FROM service_requests WHERE id=?",job).get("liftId")).longValue();
        long contract=a.insert("INSERT INTO amc_contracts(lift_id,amc_number,plan,start_date,end_date,status) VALUES(?,?,?, ?,?,'ACTIVE')",lift,UUID.randomUUID().toString(),"Standard",LocalDate.now().minusDays(1),LocalDate.now().plusDays(10));
        coverage.saveEntitlement(contract,new AmcCoverageService.Entitlement("BREAKDOWN","VISIT",true,LocalDate.now().minusDays(1),LocalDate.now().plusDays(10),"Included visit"));
        complete();
        var rule=finance.rule(new TechnicianFinanceService.Rule("BREAKDOWN","FIXED_VISIT",null,amount("100.00"),LocalDate.now().minusDays(1),null));finance.approveRule(id(rule));
        var commission=new TechnicianFinanceService.Accrue(tech,job,null,id(rule),"covered-visit",amount("100.00"),"Completed covered visit");
        assertThrows(CommerceException.class,()->finance.accrue(commission));
        coverage.capture(job,"VISIT",LocalDate.now());
        assertThrows(CommerceException.class,()->finance.accrue(commission));
        long payment=a.insert("INSERT INTO payment_records(customer_id,amc_contract_id,created_by_user_id,amount,currency,purpose,status,created_at,updated_at) VALUES(?,?,?,?, 'INR','AMC_PURCHASE','SUCCEEDED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",customerProfile,contract,admin.getId(),amount("1000.00"));
        var accrued=finance.accrue(commission);
        assertEquals(amount("100.00"),accrued.get("calculatedAmount"));
        finance.decideCommission(id(accrued),new TechnicianFinanceService.Decision(true,"Covered visit and plan payment confirmed"));
        long refund=a.insert("INSERT INTO payment_refunds(payment_id,requested_by_user_id,amount,currency,status,reason,created_at,updated_at) VALUES(?,?,100,'INR','REQUESTED','Customer review',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",payment,customer.getId());
        var payout=new TechnicianFinanceService.Batch(tech,LocalDate.now().minusDays(1),LocalDate.now(),"AMC-"+UUID.randomUUID(),List.of(id(accrued)));
        assertThrows(CommerceException.class,()->finance.batch(payout));
        a.db.update("UPDATE payment_refunds SET status='SUCCEEDED' WHERE id=?",refund);
        assertThrows(CommerceException.class,()->finance.batch(payout));
    }

    @Test void customerTimelineOnlyShowsEventsThatHaveOccurred(){
        var draft=quotations.draft(job,new ServiceQuotationDtos.Create(null,"Brake repair",amount("1000.00"),BigDecimal.ZERO,"INR",null,null));
        actor.set(customer);
        @SuppressWarnings("unchecked") var before=(List<Map<String,Object>>)jobs.view(job).get("customerTimeline");
        assertFalse(before.stream().anyMatch(e->"Quotation shared".equals(e.get("title"))));
        actor.set(admin);quotations.release(draft.id());
        actor.set(customer);
        @SuppressWarnings("unchecked") var after=(List<Map<String,Object>>)jobs.view(job).get("customerTimeline");
        assertTrue(after.stream().anyMatch(e->"Quotation shared".equals(e.get("title"))));
        assertFalse(after.stream().anyMatch(e->"Payment confirmed".equals(e.get("title"))));
        assertFalse(after.stream().anyMatch(e->"Payment receipt available".equals(e.get("title"))));
        actor.set(admin);long invoice=paidInvoice();
        a.db.update("UPDATE invoices SET issued_at=CURRENT_TIMESTAMP WHERE id=?",invoice);
        long payment=a.db.queryForObject("SELECT id FROM payment_records WHERE invoice_id=?",Long.class,invoice);
        a.db.update("UPDATE payment_records SET status='REFUNDED',verified_at=CURRENT_TIMESTAMP WHERE id=?",payment);
        a.insert("INSERT INTO payment_refunds(payment_id,requested_by_user_id,amount,currency,status,reason,created_at,updated_at) VALUES(?,?,100,'INR','SUCCEEDED','Customer refund',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",payment,customer.getId());
        actor.set(customer);
        @SuppressWarnings("unchecked") var completed=(List<Map<String,Object>>)jobs.view(job).get("customerTimeline");
        assertTrue(completed.stream().anyMatch(e->"Invoice available".equals(e.get("title"))));
        assertTrue(completed.stream().anyMatch(e->"Payment confirmed".equals(e.get("title"))));
        assertTrue(completed.stream().anyMatch(e->"Payment receipt available".equals(e.get("title"))));
        assertTrue(completed.stream().anyMatch(e->"Refund completed".equals(e.get("title"))));
    }

    @Test void inspectionSafetyAndAdditionalWorkAreBackendEnforced(){
        actor.set(technician);
        jobs.inspect(job,new ServiceFinanceWorkflow.Inspection("Fault",null,null,null,null,null,null,null,null,List.of(),null,true));
        actor.set(customer);assertTrue(((List<?>)jobs.view(job).get("inspections")).isEmpty());actor.set(technician);
        var input=new ServiceFinanceWorkflow.Inspection("Fault","Unsafe brake","Worn brake","CRITICAL","UNSAFE","Replace brake","Two technicians",90,null,List.of(attachment));
        jobs.inspect(job,input);
        assertEquals(1,a.count("SELECT COUNT(*) FROM service_inspections WHERE service_request_id=?",job));
        assertThrows(CommerceException.class,()->a.requireAttachmentDeletable(job,attachment));
        assertThrows(CommerceException.class,()->a.requireWorkApproved(job));
        long inspection=((Number)((Map<?,?>)((List<?>)jobs.view(job).get("inspections")).get(0)).get("id")).longValue();
        actor.set(admin);jobs.reviewInspection(inspection,new ServiceFinanceWorkflow.Review(true,true,"Safety checks passed after isolation"));
        assertDoesNotThrow(()->a.requireWorkApproved(job));
        actor.set(technician);jobs.requestWork(job,new ServiceFinanceWorkflow.Work("Additional brake repair","Discovered during inspection"));
        assertThrows(CommerceException.class,()->a.requireWorkApproved(job));
        long request=a.db.queryForObject("SELECT MAX(id) FROM service_work_approvals WHERE service_request_id=?",Long.class,job);
        actor.set(admin);jobs.reviewWork(request,new ServiceFinanceWorkflow.WorkReview(null,true,"Customer declined additional scope"));
        assertDoesNotThrow(()->a.requireWorkApproved(job));
        actor.set(customer);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->jobs.reviewInspection(inspection,new ServiceFinanceWorkflow.Review(true,true,"Not authorized")));
        User other=user(Role.CUSTOMER);actor.set(other);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->jobs.view(job));
    }

    @Test void draftRevisionAndPaymentStagesPreserveAcceptedInvoices(){
        var create=new ServiceQuotationDtos.Create(null,"Approved repair",amount("1000.00"),BigDecimal.ZERO,"INR",null,null);
        var draft=quotations.draft(job,create);
        actor.set(customer);assertThrows(CommerceException.class,()->quotations.customerQuotation(draft.id()));
        actor.set(admin);
        assertThrows(CommerceException.class,()->quotations.plan(draft.id(),new ServiceQuotationDtos.Plan(List.of(new ServiceQuotationDtos.Milestone("Advance","ON_APPROVAL",null,amount("900.00"))))));
        quotations.plan(draft.id(),new ServiceQuotationDtos.Plan(List.of(new ServiceQuotationDtos.Milestone("Advance","ON_APPROVAL",null,amount("300.00")),new ServiceQuotationDtos.Milestone("Final","ON_COMPLETION",null,amount("700.00")))));
        quotations.release(draft.id());actor.set(customer);var accepted=quotations.decide(draft.id(),new ServiceQuotationDtos.Decision(ServiceQuotationDtos.ServiceQuotationDecision.ACCEPT));
        assertNotNull(accepted.approvedTermsHash());assertEquals(64,accepted.approvedTermsHash().length());assertNotNull(accepted.invoiceId());
        assertEquals(2,a.count("SELECT COUNT(*) FROM service_payment_milestones WHERE quotation_id=? AND invoice_id IS NOT NULL",draft.id()));
        long finalInvoice=a.db.queryForObject("SELECT invoice_id FROM service_payment_milestones WHERE quotation_id=? AND due_condition='ON_COMPLETION'",Long.class,draft.id());
        assertThrows(CommerceException.class,()->a.requirePaymentStageDue(finalInvoice));
        complete();assertDoesNotThrow(()->a.requirePaymentStageDue(finalInvoice));
        assertEquals(amount("1000.00"),a.sum("SELECT SUM(i.total_amount) FROM invoices i JOIN service_payment_milestones m ON m.invoice_id=i.id WHERE m.quotation_id=?",draft.id()));
        assertThrows(CommerceException.class,()->quotations.decide(draft.id(),new ServiceQuotationDtos.Decision(ServiceQuotationDtos.ServiceQuotationDecision.ACCEPT)));
        actor.set(admin);var revision=quotations.revise(draft.id(),new ServiceQuotationDtos.Revision(new ServiceQuotationDtos.Create(null,"Extra scope",amount("200.00"),BigDecimal.ZERO,"INR",null,null),"New approved scope"));assertEquals("ADDITIONAL",revision.revisionKind());quotations.release(revision.id());
        actor.set(customer);quotations.decide(revision.id(),new ServiceQuotationDtos.Decision(ServiceQuotationDtos.ServiceQuotationDecision.ACCEPT));
        assertEquals(amount("300.00"),(BigDecimal)a.one("SELECT total_amount FROM invoices WHERE id=?",accepted.invoiceId()).get("totalAmount"));
        assertEquals(ServiceQuotationStatus.ACCEPTED,quotations.customerQuotation(draft.id()).status());
    }

    @Test void commissionAndPayoutRequireEligibilityEvidenceAndIndependentConfirmation(){
        complete();long invoice=paidInvoice();long commission=readyCommission(invoice);
        assertThrows(CommerceException.class,()->finance.accrue(new TechnicianFinanceService.Accrue(tech,job,invoice,1,"repair-1",amount("800.00"),"Duplicate")));
        var batch=finance.batch(new TechnicianFinanceService.Batch(tech,LocalDate.now().minusDays(1),LocalDate.now(),"TEST-"+UUID.randomUUID(),List.of(commission)));long batchId=id(batch);
        assertEquals(amount("80.00"),batch.get("totalAmount"));
        assertThrows(RuntimeException.class,()->finance.batch(new TechnicianFinanceService.Batch(tech,LocalDate.now().minusDays(1),LocalDate.now(),"OTHER-"+UUID.randomUUID(),List.of(commission))));
        finance.transfer(batchId,new TechnicianFinanceService.Transfer("APPROVE",null,null,"Approved"));
        assertThrows(CommerceException.class,()->finance.transfer(batchId,new TechnicianFinanceService.Transfer("INITIATE","BANK-1",null,"Not configured")));
        finance.settings(new TechnicianFinanceService.Settings("WEEKLY",true));finance.transfer(batchId,new TechnicianFinanceService.Transfer("INITIATE","BANK-"+UUID.randomUUID(),null,"Transfer initiated"));
        var proof=evidence.upload(batchId,null,pdf());
        assertThrows(CommerceException.class,()->finance.transfer(batchId,new TechnicianFinanceService.Transfer("CONFIRM","CONFIRM-1",id(proof),"Self verification blocked")));
        finance.transfer(batchId,new TechnicianFinanceService.Transfer("UNCERTAIN",null,null,"Awaiting bank reconciliation"));
        actor.set(checker);var paid=finance.transfer(batchId,new TechnicianFinanceService.Transfer("CONFIRM","CONFIRM-"+UUID.randomUUID(),id(proof),"Bank evidence reconciled"));assertEquals("SUCCEEDED",paid.get("status"));
        assertEquals("PAID",a.one("SELECT status FROM technician_commissions WHERE id=?",commission).get("status"));
        assertEquals(1,a.count("SELECT COUNT(*) FROM finance_notification_outbox WHERE payout_batch_id=? AND user_id=? AND status='PENDING'",batchId,technician.getId()));
        assertThrows(CommerceException.class,()->finance.transfer(batchId,new TechnicianFinanceService.Transfer("CONFIRM","AGAIN",id(proof),"Duplicate")));
        actor.set(technician);var earnings=finance.earnings();assertEquals(amount("80.00"),((Map<?,?>)earnings.get("summary")).get("paid"));assertThrows(org.springframework.security.access.AccessDeniedException.class,()->finance.rules());
    }

    @Test void financialReportUsesRecordedInrValuesAndCommissionAdjustments(){
        complete();long invoice=paidInvoice();long commission=readyCommission(invoice);
        finance.adjust(commission,new TechnicianFinanceService.Adjustment(amount("-10.00"),null,"Reviewed correction"));
        var report=finance.report();@SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)report.get("jobs");
        var row=rows.stream().filter(r->((Number)r.get("id")).longValue()==job).findFirst().orElseThrow();
        assertEquals(amount("1000.00"),row.get("collected"));assertEquals(amount("70.00"),row.get("commissions"));assertEquals(amount("930.00"),row.get("recordedContribution"));
        var filtered=finance.report(Map.of("customerProfileId",String.valueOf(customerProfile),"technicianProfileId",String.valueOf(tech),"jobStatus","COMPLETED","dateFrom",LocalDate.now().toString(),"dateTo",LocalDate.now().toString()));
        assertEquals(1,((List<?>)filtered.get("jobs")).size());assertEquals(amount("930.00"),((Map<?,?>)filtered.get("summary")).get("recordedContribution"));
        assertTrue(((List<?>)finance.report(Map.of("jobStatus","CANCELLED","customerProfileId",String.valueOf(customerProfile))).get("jobs")).isEmpty());
        assertThrows(CommerceException.class,()->finance.report(Map.of("paymentMethod","UNTRUSTED")));
        actor.set(technician);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->finance.report());
    }

    @Test void expenseReceiptsStayPrivateAndNeedApprovalBeforeReimbursement(){
        actor.set(technician);var proof=evidence.upload(null,job,pdf());
        finance.expense(new TechnicianFinanceService.Expense(job,"TRAVEL","Approved transport",amount("125.50"),LocalDate.now(),id(proof),"Site visit","expense-"+UUID.randomUUID()));
        long claim=id(finance.expenses().get(0));actor.set(admin);
        assertThrows(CommerceException.class,()->finance.reviewExpense(claim,new TechnicianFinanceService.ExpenseReview("REIMBURSE","Premature","BANK-1")));
        finance.reviewExpense(claim,new TechnicianFinanceService.ExpenseReview("APPROVE","Receipt checked",null));finance.reviewExpense(claim,new TechnicianFinanceService.ExpenseReview("REIMBURSE","Bank transfer confirmed","EXP-"+UUID.randomUUID()));
        assertEquals("REIMBURSED",finance.expenses().get(0).get("status"));
        actor.set(customer);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->evidence.download(id(proof)));
    }

    @Test void partUsageCannotExceedIssuedStockAndRetriesDoNotDuplicateMovements(){
        long category=a.insert("INSERT INTO part_categories(name,is_active,created_at,updated_at) VALUES(?,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)","Category "+UUID.randomUUID());
        long part=a.insert("INSERT INTO part_items(category_id,name,sku,unit,quantity_on_hand,reorder_threshold,is_active,version,created_at,updated_at) VALUES(?,'Brake',?,'each',0,0,TRUE,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",category,UUID.randomUUID().toString());
        long request=a.insert("INSERT INTO service_request_part_requests(service_request_id,technician_profile_id,status,created_at) VALUES(?,?,'ISSUED',CURRENT_TIMESTAMP)",job,tech);
        long line=a.insert("INSERT INTO service_request_part_lines(request_id,part_item_id,requested_quantity,approved_quantity,issued_quantity) VALUES(?,?,2,2,2)",request,part);
        actor.set(technician);var received=new ServiceFinanceWorkflow.Usage(line,"RECEIVED",2,"Received sealed parts","receive-123");jobs.usage(job,received);jobs.usage(job,received);
        assertEquals(2,a.count("SELECT SUM(quantity) FROM job_part_usage WHERE part_line_id=?",line));
        jobs.usage(job,new ServiceFinanceWorkflow.Usage(line,"INSTALLED",1,"Installed brake","install-123"));
        jobs.usage(job,new ServiceFinanceWorkflow.Usage(line,"RETURNED",1,"Unused sealed part","return-123"));
        assertThrows(CommerceException.class,()->jobs.usage(job,new ServiceFinanceWorkflow.Usage(line,"RETURNED",1,"Cannot return twice","return-456")));
        assertEquals(1,a.count("SELECT quantity_on_hand FROM part_items WHERE id=?",part));
    }

    @Test void refundsNeedAuditableAdjustmentsAndCancelledBatchesReleaseTheirItems(){
        complete();long invoice=paidInvoice();long commission=readyCommission(invoice);
        long payment=a.db.queryForObject("SELECT id FROM payment_records WHERE invoice_id=?",Long.class,invoice);
        long refund=a.insert("INSERT INTO payment_refunds(payment_id,requested_by_user_id,amount,currency,status,reason,created_at,updated_at) VALUES(?,?,100,'INR','REQUESTED','Customer refund',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",payment,customer.getId());
        assertThrows(CommerceException.class,()->finance.batch(new TechnicianFinanceService.Batch(tech,LocalDate.now().minusDays(1),LocalDate.now(),"BLOCKED-"+UUID.randomUUID(),List.of(commission))));
        a.db.update("UPDATE payment_refunds SET status='SUCCEEDED' WHERE id=?",refund);
        finance.adjust(commission,new TechnicianFinanceService.Adjustment(amount("-10.00"),refund,"Net labour portion of the confirmed refund"));
        var first=finance.batch(new TechnicianFinanceService.Batch(tech,LocalDate.now().minusDays(1),LocalDate.now(),"FIRST-"+UUID.randomUUID(),List.of(commission)));assertEquals(amount("70.00"),first.get("totalAmount"));
        finance.transfer(id(first),new TechnicianFinanceService.Transfer("CANCEL",null,null,"Cancelled before transfer"));
        var second=finance.batch(new TechnicianFinanceService.Batch(tech,LocalDate.now().minusDays(1),LocalDate.now(),"RETRY-"+UUID.randomUUID(),List.of(commission)));assertEquals(amount("70.00"),second.get("totalAmount"));
        assertEquals("READY",a.one("SELECT status FROM technician_commissions WHERE id=?",commission).get("status"));
    }

    @Test void uncertainBankTransferCannotBeRetriedUntilIndependentEvidenceReconciliation(){
        complete();long invoice=paidInvoice();long commission=readyCommission(invoice);var b=finance.batch(new TechnicianFinanceService.Batch(tech,LocalDate.now().minusDays(1),LocalDate.now(),"UNCERTAIN-"+UUID.randomUUID(),List.of(commission)));long batch=id(b);
        finance.transfer(batch,new TechnicianFinanceService.Transfer("APPROVE",null,null,"Reviewed"));finance.settings(new TechnicianFinanceService.Settings("WEEKLY",true));finance.transfer(batch,new TechnicianFinanceService.Transfer("INITIATE","TRANSFER-"+UUID.randomUUID(),null,"Sent to bank"));finance.transfer(batch,new TechnicianFinanceService.Transfer("UNCERTAIN",null,null,"Bank response unavailable"));
        assertThrows(CommerceException.class,()->finance.transfer(batch,new TechnicianFinanceService.Transfer("CANCEL",null,null,"Unsafe retry")));
        actor.set(checker);assertThrows(CommerceException.class,()->finance.transfer(batch,new TechnicianFinanceService.Transfer("RECONCILE_FAILED","BANK-FAILED",null,"No funds moved")));
        var proof=evidence.upload(batch,null,pdf());assertEquals("FAILED",finance.transfer(batch,new TechnicianFinanceService.Transfer("RECONCILE_FAILED","BANK-FAILED",id(proof),"Bank confirms funds never moved")).get("status"));
        assertDoesNotThrow(()->finance.batch(new TechnicianFinanceService.Batch(tech,LocalDate.now().minusDays(1),LocalDate.now(),"SAFE-RETRY-"+UUID.randomUUID(),List.of(commission))));
    }

    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentPayoutRequestsLockEachCommissionIntoAtMostOneActiveBatch() throws Exception {
        complete();long invoice=paidInvoice();long commission=readyCommission(invoice);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
        try{java.util.concurrent.Callable<Boolean> submit=()->{start.await();try{finance.batch(new TechnicianFinanceService.Batch(tech,LocalDate.now().minusDays(1),LocalDate.now(),"RACE-"+UUID.randomUUID(),List.of(commission)));return true;}catch(RuntimeException conflict){return false;}};
            var one=executor.submit(submit);var two=executor.submit(submit);start.countDown();assertEquals(1,(one.get()?1:0)+(two.get()?1:0));
            assertEquals(1,a.count("SELECT COUNT(*) FROM technician_payout_items WHERE commission_id=? AND status='ACTIVE'",commission));
        }finally{executor.shutdownNow();}
    }
}
