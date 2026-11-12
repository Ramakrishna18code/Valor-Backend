package com.valor.auth;

import com.valor.reporting.ServiceReminderService;

import com.valor.auth.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:service_reminder_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc @ActiveProfiles("test")
class ServiceReminderIntegrationTest {
    @Autowired JdbcTemplate db; @Autowired ServiceReminderService reminders; @Autowired MockMvc mvc;
    @Autowired UserRepo users; @Autowired CustomerRepo customers; @Autowired TechRepo technicians; @Autowired JwtService jwt;
    @MockitoBean Clock clock;
    final LocalDate today=LocalDate.of(2026,11,12);

    @Test void duePendingOverdueEmergencyDedupRescheduleCompletionReportsAndAccess() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-11T18:45:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        User admin=user(Role.ADMIN), customerUser=user(Role.CUSTOMER), technicianUser=user(Role.TECHNICIAN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin.getId(),null,List.of()));
        try {
            CustomerProfile owner=new CustomerProfile();owner.user=customerUser;owner.fullName="Reminder customer";
            long customer=customers.saveAndFlush(owner).id;
            TechnicianProfile tech=new TechnicianProfile();tech.user=technicianUser;long technician=technicians.saveAndFlush(tech).id;
            long building=insert("INSERT INTO buildings(customer_id,building_name) VALUES(?,?)",customer,"Reminder tower");
            long lift=insert("INSERT INTO lifts(building_id,name,lift_number,next_maintenance_date) VALUES(?,'Service lift','LFT-102',?)",building,today.plusDays(1));
            long contract=insert("INSERT INTO amc_contracts(lift_id,amc_number,plan,start_date,end_date) VALUES(?,'AMC-102','Standard',?,?)",lift,today.minusDays(30),today.plusDays(30));
            insert("INSERT INTO amc_service_entitlements(contract_id,service_type,scope,covered,included_visit_limit,effective_from,effective_until,updated_by_user_id) VALUES(?,'ROUTINE_MAINTENANCE','VISIT',TRUE,3,?,?,?)",contract,today.minusDays(30),today.plusDays(30),admin.getId());
            long dueLift=insert("INSERT INTO lifts(building_id,name,lift_number,next_maintenance_date) VALUES(?,'AMC due lift','LFT-103',?)",building,today.plusDays(1));
            long dueContract=insert("INSERT INTO amc_contracts(lift_id,amc_number,plan,start_date,end_date) VALUES(?,'AMC-103','Standard',?,?)",dueLift,today.minusDays(30),today.plusDays(30));
            insert("INSERT INTO amc_service_entitlements(contract_id,service_type,scope,covered,included_visit_limit,effective_from,effective_until,updated_by_user_id) VALUES(?,'ROUTINE_MAINTENANCE','VISIT',TRUE,3,?,?,?)",dueContract,today.minusDays(30),today.plusDays(30),admin.getId());
            long emergency=job(customer,lift,"PENDING","EMERGENCY"),overdue=job(customer,lift,"ASSIGNED","BREAKDOWN"),
                    near=job(customer,lift,"ASSIGNED","BREAKDOWN"),scheduled=job(customer,lift,"ASSIGNED","ROUTINE_MAINTENANCE"),completed=job(customer,lift,"COMPLETED","BREAKDOWN"),
                    approval=job(customer,lift,"PENDING","BREAKDOWN");
            assign(overdue,technician,admin.getId());assign(near,technician,admin.getId());assign(scheduled,technician,admin.getId());assign(completed,technician,admin.getId());
            long overdueVisit=visit(overdue,technician,today.minusDays(1));visit(near,technician,today.plusDays(5));visit(scheduled,technician,today.plusDays(1));
            long completedVisit=visit(completed,technician,today.minusDays(2));db.update("UPDATE service_visits SET status='COMPLETED' WHERE id=?",completedVisit);
            long usedAmcVisit=job(customer,dueLift,"COMPLETED","ROUTINE_MAINTENANCE");assign(usedAmcVisit,technician,admin.getId());
            long usedVisit=visit(usedAmcVisit,technician,today.minusDays(5));db.update("UPDATE service_visits SET status='COMPLETED' WHERE id=?",usedVisit);
            insert("INSERT INTO service_request_coverage_decisions(service_request_id,version_number,contract_id,service_type,scope,service_date,outcome,reason) VALUES(?,1,?,'ROUTINE_MAINTENANCE','VISIT',?,'COVERED','Fixture')",usedAmcVisit,dueContract,today.minusDays(5));
            insert("INSERT INTO service_request_coverage_decisions(service_request_id,version_number,contract_id,service_type,scope,service_date,outcome,reason) VALUES(?,1,?,'ROUTINE_MAINTENANCE','VISIT',?,'COVERED','Fixture')",scheduled,contract,today.plusDays(1));
            insert("INSERT INTO service_quotations(quotation_number,customer_id,service_request_id,created_by_user_id,description,subtotal,total_amount,status) VALUES(?,?,?,?,'Fixture',100,100,'ISSUED')",UUID.randomUUID().toString(),customer,approval,admin.getId());
            var summary=reminders.summary();assertEquals(today,summary.asOf());assertEquals("Asia/Kolkata",summary.timezone());
            assertEquals(1,summary.overdue());assertEquals(1,summary.emergencies());assertEquals(1,summary.customerFollowUps());assertTrue(summary.amcDueSoon()>=1);
            var all=reminders.list("ALL",null,null,null,null,"date","asc",0,20).items();
            assertTrue(all.stream().anyMatch(row->row.sourceId()==emergency&&"CRITICAL".equals(row.priority())));
            assertTrue(all.stream().anyMatch(row->row.sourceId()==overdue&&"CRITICAL".equals(row.priority())));
            assertTrue(all.stream().anyMatch(row->row.sourceId()==approval&&"CUSTOMER_APPROVAL".equals(row.stage())));
            assertTrue(all.stream().anyMatch(row->row.sourceId()==scheduled&&"AMC".equals(row.serviceType())
                    && row.remainingAmcServices()==3 && "AMC-102".equals(row.amcContractNumber()) && row.liftId()==lift));
            assertTrue(all.stream().anyMatch(row->row.sourceId()==completed&&today.minusDays(2).equals(row.scheduledDate())
                    && today.minusDays(2).equals(row.lastCompletedVisitDate())));
            assertTrue(all.stream().anyMatch(row->"AMC_DUE".equals(row.sourceType())&&row.remainingAmcServices()==2
                    && today.minusDays(5).equals(row.lastCompletedVisitDate())));
            assertEquals(1,reminders.list("OVERDUE",null,null,null,null,"date","asc",0,20).totalElements());
            assertEquals(summary.emergencies(),reminders.list("ALL",null,null,null,null,"date","asc","emergencies",0,20).totalElements());
            assertEquals(summary.amcDueSoon(),reminders.list("ALL",null,null,null,null,"date","asc","amcDueSoon",0,20).totalElements());
            reminders.scan();reminders.scan();
            assertEquals(1L,db.queryForObject("SELECT COUNT(*) FROM service_reminder_alerts WHERE source_id=? AND alert_kind='OVERDUE'",Long.class,overdue));
            assertEquals(1L,db.queryForObject("SELECT COUNT(*) FROM service_reminder_alerts WHERE source_id=? AND alert_kind='DUE_SOON'",Long.class,near));
            assertTrue(reminders.alerts(0,20).unreadCount()>0);
            long alert=reminders.alerts(0,20).items().getFirst().id();assertTrue(reminders.readAlert(alert).read());assertTrue(reminders.resolveAlert(alert).resolved());
            db.update("UPDATE service_visits SET scheduled_date=? WHERE id=?",today.plusDays(3),overdueVisit);reminders.scan();
            assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM service_reminder_alerts WHERE source_id=? AND alert_kind='OVERDUE' AND status='OPEN'",Long.class,overdue));
            db.update("UPDATE service_visits SET status='CANCELLED' WHERE id=?",overdueVisit);
            db.update("UPDATE service_requests SET status='COMPLETED' WHERE id=?",scheduled);reminders.scan();
            assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM service_reminder_alerts WHERE source_id=? AND status='OPEN'",Long.class,scheduled));
            var report=reminders.generateDailyReport();assertEquals(report.id(),reminders.generateDailyReport().id());
            assertEquals(1L,db.queryForObject("SELECT COUNT(*) FROM daily_service_reminder_reports",Long.class));assertTrue(reminders.reportCsv().contains("Total pending"));
            SecurityContextHolder.clearContext();
            mvc.perform(get("/api/v1/admin/service-reminders/summary")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/admin/service-reminders/summary").header("Authorization","Bearer "+jwt.issue(customerUser))).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/admin/service-reminders/summary").header("Authorization","Bearer "+jwt.issue(admin))).andExpect(status().isOk());
            mvc.perform(get("/api/v1/admin/service-reminders/alerts").header("Authorization","Bearer "+jwt.issue(customerUser))).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/admin/service-reminders/alerts").header("Authorization","Bearer "+jwt.issue(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isArray());
            mvc.perform(get("/api/v1/technician/me/helper-assignments").header("Authorization","Bearer "+jwt.issue(technicianUser))).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        } finally {SecurityContextHolder.clearContext();}
    }
    private User user(Role role){User value=new User();value.setEmail(UUID.randomUUID()+"@example.test");value.setRole(role);return users.saveAndFlush(value);}
    private long job(long customer,long lift,String status,String type){return insert("INSERT INTO service_requests(customer_id,lift_id,service_id,title,description,status,service_type,priority) VALUES(?,?,?,'Fixture','Fixture',?,?,?)",customer,lift,UUID.randomUUID().toString(),status,type,"EMERGENCY".equals(type)?"EMERGENCY":"MEDIUM");}
    private void assign(long job,long tech,long admin){insert("INSERT INTO technician_assignments(service_request_id,technician_id,assigned_by_user_id) VALUES(?,?,?)",job,tech,admin);}
    private long visit(long job,long tech,LocalDate date){return insert("INSERT INTO service_visits(service_request_id,technician_profile_id,scheduled_date,start_time,end_time) VALUES(?,?,?,'09:00:00','10:00:00')",job,tech,date);}
    private long insert(String sql,Object... args){var key=new GeneratedKeyHolder();db.update(connection->{var statement=connection.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement;},key);return ((Number)Objects.requireNonNull(key.getKeys()).get("ID")).longValue();}
}
