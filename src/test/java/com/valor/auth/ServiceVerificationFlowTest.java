package com.valor.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.valor.workflow.*;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Each HTTP transaction really commits to this isolated in-memory database, including concurrent tests.
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:service_verification_flow;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "app.attachments.local-root=target/test-request-attachments"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceVerificationFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepo users;
    @Autowired CustomerRepo customers;
    @Autowired TechRepo technicians;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate db;
    @Autowired DataSource dataSource;
    @Autowired EntityManager em;
    @Autowired Flyway flyway;
    @Autowired Environment environment;
    @Autowired RequestMappingHandlerMapping mappings;

    record Fixture(User admin, User customer, User technician, Long profileId, Long technicianId, long liftId, long requestId) {}
    private User user(Role role) {
        User user = new User(); user.setEmail(UUID.randomUUID() + "@example.test"); user.setRole(role);
        return users.saveAndFlush(user);
    }
    private TechnicianProfile technician() {
        TechnicianProfile profile = new TechnicianProfile(); profile.user = user(Role.TECHNICIAN);
        return technicians.saveAndFlush(profile);
    }
    private JsonNode call(MockHttpServletRequestBuilder request, User actor, Object body, int expected) throws Exception {
        if (actor != null) request.header("Authorization", "Bearer " + jwt.issue(actor));
        if (body != null) request.contentType("application/json").content(json.writeValueAsString(body));
        String text = mvc.perform(request).andExpect(status().is(expected)).andExpect(jsonPath("$.status").value(expected))
                .andExpect(jsonPath("$.timestamp").exists()).andReturn().getResponse().getContentAsString();
        for (String secret : List.of("passwordHash", "otpHash", "tokenHash", "accessToken", "refreshToken", "stackTrace", "com.valor", "org.hibernate")) {
            assertFalse(text.contains(secret));
        }
        JsonNode envelope = json.readTree(text); assertEquals(expected < 400, envelope.get("success").asBoolean());
        return envelope.get("data");
    }
    private Map<String,Object> creation(long lift) {
        return Map.of("liftId", lift, "title", "Service needed", "description", "Test issue", "serviceType", "BREAKDOWN");
    }
    private Fixture fixture() throws Exception {
        User admin = user(Role.ADMIN), customer = user(Role.CUSTOMER);
        CustomerProfile owner = new CustomerProfile(); owner.setUser(customer); owner.setFullName("Workflow customer");
        customers.saveAndFlush(owner);
        TechnicianProfile tech = technician();
        long building = call(post("/api/v1/buildings"), admin, Map.of("customerProfileId", owner.getId(), "buildingName", "Tower"), 200).get("id").asLong();
        long lift = call(post("/api/v1/lifts"), admin, Map.of("buildingId", building, "name", "Lift"), 200).get("id").asLong();
        long request = call(post("/api/v1/service-requests"), customer, creation(lift), 200).at("/request/id").asLong();
        return new Fixture(admin, customer, tech.getUser(), owner.getId(), tech.getId(), lift, request);
    }
    @Test void locationEndpointsAreRegisteredAndValidateInput() throws Exception {
        User customer=user(Role.CUSTOMER);
        CustomerProfile owner=new CustomerProfile();owner.setUser(customer);owner.setFullName("Location QA");customers.saveAndFlush(owner);
        call(get("/api/v1/locations/search").param("q","x"),customer,null,400);
        call(get("/api/v1/locations/reverse").param("latitude","91").param("longitude","0"),customer,null,400);
        mvc.perform(get("/api/v1/locations/search").param("q","Hyderabad")).andExpect(status().isUnauthorized());
    }
    @Test void jobContainsCustomerOwnedSiteCoordinates() throws Exception {
        Fixture f=fixture();
        long building=db.queryForObject("select building_id from lifts where id=?",Long.class,f.liftId());
        call(put("/api/v1/customers/me/buildings/"+building),f.customer(),Map.of("buildingName","Tower","address","Street 10","city","Hyderabad","state","Telangana","latitude",17.4,"longitude",78.5),200);
        assign(f);
        JsonNode job=call(get("/api/v1/technician/me/jobs/"+f.requestId()),f.technician(),null,200).get("request");
        assertEquals(17.4,job.get("buildingLatitude").asDouble());
        assertEquals(78.5,job.get("buildingLongitude").asDouble());
        assertTrue(job.get("buildingAddress").asText().contains("Hyderabad"));
        call(put("/api/v1/customers/me/buildings/"+building),user(Role.CUSTOMER),Map.of("buildingName","Other","latitude",1,"longitude",2),403);
    }
    private String path(Fixture f) { return "/api/v1/service-requests/" + f.requestId(); }
    private long assign(Fixture f) throws Exception { return assign(f, f.technicianId()); }
    private long assign(Fixture f, long technicianId) throws Exception {
        return call(post(path(f) + "/assignments"), f.admin(), Map.of("technicianProfileId", technicianId, "notes", "Assignment note"), 200).at("/activeAssignment/id").asLong();
    }
    private void accept(Fixture f, long assignment, User technician) throws Exception {
        call(post(path(f) + "/assignments/" + assignment + "/accept"), technician, null, 200);
    }
    private JsonNode transition(Fixture f, User actor, String to, String notes, int status) throws Exception {
        return call(post(path(f) + "/status"), actor, Map.of("toStatus", to, "notes", notes), status);
    }
    private int events(Fixture f) { return db.queryForObject("select count(*) from service_status_history where service_request_id=?", Integer.class, f.requestId()); }
    private String state(Fixture f) { return db.queryForObject("select status from service_requests where id=?", String.class, f.requestId()); }
    private JsonNode report(Fixture f, User tech, int status) throws Exception {
        return call(post("/api/v1/technician/me/jobs/" + f.requestId() + "/report"), tech,
                Map.of("diagnosis", "Diagnosed", "workPerformed", "Repaired", "testingResult", "Passed"), status);
    }
    private void reach(Fixture f, String target) throws Exception {
        if (target.equals("PENDING")) return;
        long a = assign(f);
        if (target.equals("ASSIGNED")) return;
        accept(f, a, f.technician());
        if (target.equals("ACCEPTED")) return;
        for (String next : List.of("ON_THE_WAY", "REACHED_SITE", "DIAGNOSIS")) {
            transition(f, f.technician(), next, "Progress", 200); if (target.equals(next)) return;
        }
        if (target.equals("WAITING_FOR_PARTS")) { transition(f, f.technician(), target, "Waiting", 200); return; }
        transition(f, f.technician(), "REPAIR_IN_PROGRESS", "Repair", 200);
        if (target.equals("REPAIR_IN_PROGRESS")) return;
        transition(f, f.technician(), "TESTING", "Test", 200);
        if (target.equals("TESTING")) return;
        if (target.equals("COMPLETED")) report(f, f.technician(), 200);
        transition(f, f.admin(), target, "Terminal reason", 200);
    }


    private JsonNode code(Fixture f, String kind, User actor) throws Exception {
        return call(get(path(f)+"/"+kind+"-otp"), actor, null, 200);
    }
    private JsonNode send(Fixture f, String kind, int expected) throws Exception {
        return call(post("/api/v1/technician/me/jobs/"+f.requestId()+"/"+kind+"-otp/request"), f.technician(), Map.of(), expected);
    }
    private void verify(Fixture f, String kind, JsonNode otp, int expected) throws Exception {
        call(post("/api/v1/technician/me/jobs/"+f.requestId()+"/"+kind+"-otp/verify"), f.technician(), Map.of("otpId",otp.get("id").asLong(),"otp",otp.get("code").asText()), expected);
    }
    private void arrive(Fixture f) throws Exception {
        accept(f, assign(f), f.technician());
        transition(f,f.technician(),"ON_THE_WAY","Travel",200);
        transition(f,f.technician(),"REACHED_SITE","Arrived",200);
    }
    private void testing(Fixture f) throws Exception {
        arrive(f); send(f,"arrival",200); verify(f,"arrival",code(f,"arrival",f.customer()),200);
        transition(f,f.technician(),"DIAGNOSIS","Diagnosed",200);
        transition(f,f.technician(),"REPAIR_IN_PROGRESS","Repair",200);
        transition(f,f.technician(),"TESTING","Test",200);
    }
    @Test void twoCustomerCodesNotifyOwnerAndGateWorkAndCompletion() throws Exception {
        Fixture f=fixture(), other=fixture(); arrive(f);
        send(f,"completion",409);
        assertTrue(send(f,"arrival",200).get("code").isNull());
        JsonNode start=code(f,"arrival",f.customer()); assertTrue(start.get("code").asText().matches("[1-9][0-9]{5}"));
        assertTrue(code(f,"arrival",f.technician()).get("code").isNull());
        call(get(path(f)+"/arrival-otp"),other.customer(),null,403);
        JsonNode inbox=call(get("/api/v1/notifications"),f.customer(),null,200);
        assertTrue(inbox.toString().contains("Start-work code ready")); assertFalse(inbox.toString().contains(start.get("code").asText()));
        transition(f,f.technician(),"DIAGNOSIS","Blocked",409);
        verify(f,"arrival",start,200); assertTrue(code(f,"arrival",f.customer()).get("code").isNull());
        transition(f,f.technician(),"DIAGNOSIS","Diagnosed",200);
        transition(f,f.technician(),"REPAIR_IN_PROGRESS","Repair",200);
        transition(f,f.technician(),"TESTING","Test",200); report(f,f.technician(),200);
        transition(f,f.technician(),"COMPLETED","Blocked",409);
        assertTrue(send(f,"completion",200).get("code").isNull()); JsonNode finish=code(f,"completion",f.customer());
        assertEquals(1, db.queryForObject("select count(*) from completion_otps where service_request_id=?", Integer.class, f.requestId()));
        inbox=call(get("/api/v1/notifications"),f.customer(),null,200); assertTrue(inbox.toString().contains("Completion code ready")); assertFalse(inbox.toString().contains(finish.get("code").asText()));
        verify(f,"completion",finish,200); transition(f,f.technician(),"COMPLETED","Done",200);
        assertEquals("VERIFIED",code(f,"completion",f.customer()).get("status").asText());
        assertTrue(code(f,"completion",f.technician()).get("code").isNull());
    }
    @Test void resendsInvalidateOldCodesAndFailedAttemptsPersist() throws Exception {
        Fixture f=fixture(); testing(f); send(f,"completion",200); JsonNode old=code(f,"completion",f.customer());
        send(f,"completion",200); verify(f,"completion",old,400); JsonNode current=code(f,"completion",f.customer());
        String verifyPath="/api/v1/technician/me/jobs/"+f.requestId()+"/completion-otp/verify";
        for(int remaining=2;remaining>=0;remaining--){call(post(verifyPath),f.technician(),Map.of("otpId",current.get("id").asLong(),"otp","000000"),400);assertEquals(remaining,code(f,"completion",f.customer()).get("attemptsRemaining").asInt());}
        assertEquals("LOCKED",code(f,"completion",f.customer()).get("status").asText()); verify(f,"completion",current,400);
        send(f,"completion",200); current=code(f,"completion",f.customer());
        db.update("update completion_otps set expires_at=? where id=?",java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1),current.get("id").asLong());
        assertEquals("EXPIRED",code(f,"completion",f.customer()).get("status").asText()); verify(f,"completion",current,400);
    }
    @Test void sameTechnicianReassignmentInvalidatesOldArrivalAndRequiresAcceptance() throws Exception {
        Fixture f=fixture(); arrive(f); send(f,"arrival",200); JsonNode old=code(f,"arrival",f.customer());verify(f,"arrival",old,200);
        long next=assign(f); assertTrue(code(f,"arrival",f.customer()).isNull()); send(f,"arrival",409);
        accept(f,next,f.technician()); assertEquals("REACHED_SITE",state(f)); transition(f,f.technician(),"DIAGNOSIS","Blocked",409);
        verify(f,"arrival",old,400); send(f,"arrival",200); verify(f,"arrival",code(f,"arrival",f.customer()),200); transition(f,f.technician(),"DIAGNOSIS","Confirmed",200);
    }
    @Test void installationLocationWithoutLiftIsOptionalUntilTravelAndDoesNotThrow() throws Exception {
        Fixture f=fixture(); long request=call(post("/api/v1/service-requests"),f.customer(),Map.of("title","New lift","description","Install","serviceType","INSTALLATION"),200).at("/request/id").asLong();
        Fixture installation=new Fixture(f.admin(),f.customer(),f.technician(),f.profileId(),f.technicianId(),0,request);
        accept(installation,assign(installation),installation.technician());
        String location="/api/v1/technician/me/jobs/"+request+"/location";
        assertTrue(call(get(location),f.technician(),null,200).isNull());
        transition(installation,f.technician(),"ON_THE_WAY","Travel",200);
        assertTrue(call(get(location),f.technician(),null,200).isNull());
        call(post(location),f.technician(),Map.of("latitude",17.4,"longitude",78.4),200);
    }
}
