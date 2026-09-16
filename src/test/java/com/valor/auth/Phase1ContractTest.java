package com.valor.auth;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:freeze_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc @ActiveProfiles("test")
class Phase1ContractTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired UserRepo users;@Autowired CustomerRepo customers;
    @Autowired org.springframework.core.env.ConfigurableEnvironment environment;
    @Autowired TechRepo technicians;@Autowired OtpRepo otps;@Autowired JwtService jwt;@Autowired PasswordEncoder encoder;
    JsonNode call(MockHttpServletRequestBuilder r,User actor,Object body,int expected)throws Exception{
        if(actor!=null)r.header("Authorization","Bearer "+jwt.issue(actor));
        if(body!=null)r.contentType("application/json").content(json.writeValueAsString(body));
        String text=mvc.perform(r).andExpect(status().is(expected)).andExpect(jsonPath("$.success").value(expected<400))
            .andReturn().getResponse().getContentAsString();
        for(String secret:List.of("passwordHash","otpHash","tokenHash","stackTrace"))assertFalse(text.contains(secret));
        return json.readTree(text).get("data");
    }
    User user(Role role){User u=new User();u.setEmail(UUID.randomUUID()+"@example.test");u.setRole(role);u.setPasswordHash(encoder.encode("test-password"));users.saveAndFlush(u);
        if(role==Role.CUSTOMER){CustomerProfile p=new CustomerProfile();p.user=u;p.fullName="Customer";customers.saveAndFlush(p);}
        if(role==Role.TECHNICIAN){TechnicianProfile p=new TechnicianProfile();p.user=u;technicians.saveAndFlush(p);}return u;}
    User phoneCustomer(){User u=user(Role.CUSTOMER);u.setPhone("+1"+String.format("%010d",Math.abs(UUID.randomUUID().getLeastSignificantBits()%10000000000L)));return users.saveAndFlush(u);}
    JsonNode send(User u)throws Exception{return call(post("/api/v1/auth/otp/send"),null,Map.of("phone",u.getPhone()),200);}
    Map<String,Object> verify(User u,JsonNode sent){return new HashMap<>(Map.of("phone",u.getPhone(),"otp",sent.get("otp").asText(),"requestId",sent.get("requestId").asLong()));}
    @Test void registrationAndMeHaveTypedProfilesAndRejectEvenNullRole()throws Exception{
        String email=UUID.randomUUID()+"@example.test";var body=new HashMap<String,Object>(Map.of("email",email,"password","test-password","fullName","Name","companyName","Company"));
        JsonNode session=call(post("/api/v1/auth/register"),null,body,200);assertEquals("CUSTOMER",session.get("role").asText());assertEquals("Company",session.at("/customerProfile/companyName").asText());
        User u=users.findByEmail(email).orElseThrow();JsonNode me=call(get("/api/v1/me"),u,null,200);assertEquals(u.getId(),me.get("userId").asLong());assertEquals("CUSTOMER",me.get("role").asText());assertEquals("Name",me.at("/customerProfile/fullName").asText());
        body.put("email",UUID.randomUUID()+"@example.test");body.put("role",null);call(post("/api/v1/auth/register"),null,body,400);
    }
    @Test void emailOnlyStaffLoginAndNormalizedCustomerPhone()throws Exception{
        for(Role role:List.of(Role.ADMIN,Role.SUPER_ADMIN,Role.TECHNICIAN)){
            User u=user(role);String path="/api/v1/auth/login/"+(role==Role.TECHNICIAN?"technician":"admin");
            JsonNode auth=call(post(path),null,Map.of("email","  "+u.getEmail().toUpperCase(Locale.ROOT)+"  ","password","test-password"),200);
            assertEquals(role.name(),auth.get("role").asText());if(role==Role.TECHNICIAN)assertTrue(auth.get("technicianProfile").isObject());
            call(post(path),null,Map.of("identity",u.getEmail(),"password","test-password"),400);
            call(post(path),null,Map.of("email","+14155551234","password","test-password"),400);
        }
        User customer=phoneCustomer();call(post("/api/v1/auth/login/customer"),null,Map.of("identity"," "+customer.getPhone()+" ","password","test-password"),200);
    }
    @Test void otpBindsPhoneAndRequestIdAndRejectsMissingIdAndReplay()throws Exception{
        User u=phoneCustomer();JsonNode sent=send(u);assertTrue(sent.get("requestId").asLong()>0);assertTrue(sent.has("expiresAt"));assertTrue(sent.get("developmentOnly").asBoolean());
        var input=verify(u,sent);input.remove("requestId");call(post("/api/v1/auth/otp/verify"),null,input,400);
        input=verify(u,sent);input.put("phone",phoneCustomer().getPhone());call(post("/api/v1/auth/otp/verify"),null,input,400);
        input=verify(u,sent);input.put("requestId",Long.MAX_VALUE);call(post("/api/v1/auth/otp/verify"),null,input,400);
        JsonNode auth=call(post("/api/v1/auth/otp/verify"),null,verify(u,sent),200);assertEquals(u.getId(),auth.get("userId").asLong());assertTrue(auth.get("customerProfile").isObject());
        call(post("/api/v1/auth/otp/verify"),null,verify(u,sent),400);
    }
    @Test void otpExpiryLockThrottleAndCustomerOnly()throws Exception{
        User u=phoneCustomer();JsonNode sent=send(u);call(post("/api/v1/auth/otp/send"),null,Map.of("phone",u.getPhone()),400);
        OtpVerification row=otps.findById(sent.get("requestId").asLong()).orElseThrow();row.expiresAt=LocalDateTime.now().minusSeconds(1);otps.saveAndFlush(row);
        call(post("/api/v1/auth/otp/verify"),null,verify(u,sent),400);
        u=phoneCustomer();sent=send(u);var wrong=verify(u,sent);wrong.put("otp","invalid");for(int i=0;i<3;i++)call(post("/api/v1/auth/otp/verify"),null,wrong,400);
        row=otps.findById(sent.get("requestId").asLong()).orElseThrow();assertEquals(0,row.attemptsRemaining);assertNotNull(row.lockedUntil);call(post("/api/v1/auth/otp/verify"),null,verify(u,sent),400);
        User admin=user(Role.ADMIN);admin.setPhone(phoneCustomer().getPhone()+"1");users.saveAndFlush(admin);sent=send(admin);call(post("/api/v1/auth/otp/verify"),null,verify(admin,sent),400);
    }
    @Test void passwordLockoutPersistsAndPhoneIdentityAllowsPasswordlessEmail()throws Exception {
        User u=user(Role.ADMIN);var input=Map.of("email",u.getEmail(),"password","wrong-password");
        for(int i=0;i<5;i++)call(post("/api/v1/auth/login/admin"),null,input,400);
        User locked=users.findById(u.getId()).orElseThrow();assertEquals(5,locked.getFailedLoginAttempts());assertNotNull(locked.getLockedUntil());
        call(post("/api/v1/auth/login/admin"),null,Map.of("email",u.getEmail(),"password","test-password"),400);
        call(get("/api/v1/me"),u,null,401);
        String email=UUID.randomUUID()+"@example.test";
        call(post("/api/v1/auth/register"),null,Map.of("email",email,"phone","+1888"+String.format("%07d",Math.abs(UUID.randomUUID().getLeastSignificantBits()%10000000)),"fullName","Phone account"),200);
        assertNull(users.findByEmail(email).orElseThrow().getPasswordHash());
    }
    @Test void productionNeverExposesOrPersistsDevelopmentOtp()throws Exception{
        String[] active=environment.getActiveProfiles();long before=otps.count();
        try {environment.setActiveProfiles("test","prod");call(post("/api/v1/auth/otp/send"),null,Map.of("phone","+14155559876"),400);assertEquals(before,otps.count());}
        finally {environment.setActiveProfiles(active);}
    }
    @Test void logoutCannotRevokeAnotherUsersToken()throws Exception{
        User a=user(Role.ADMIN),b=user(Role.ADMIN);JsonNode session=call(post("/api/v1/auth/login/admin"),null,Map.of("email",a.getEmail(),"password","test-password"),200);
        var body=Map.of("refreshToken",session.get("refreshToken").asText());call(post("/api/v1/auth/logout"),b,body,403);call(post("/api/v1/auth/refresh"),null,body,200);
    }
    @Test void customerRoutesEnforceOwnershipAndDashboardAllowsAdmins()throws Exception{
        User a=user(Role.CUSTOMER),b=user(Role.CUSTOMER),admin=user(Role.ADMIN);
        call(put("/api/v1/customers/me"),a,Map.of("fullName","Changed","companyName","Acme"),200);
        assertEquals("Changed",call(get("/api/v1/customers/me"),a,null,200).get("fullName").asText());assertEquals("Customer",call(get("/api/v1/customers/me"),b,null,200).get("fullName").asText());
        JsonNode building=call(post("/api/v1/customers/me/buildings"),a,Map.of("buildingName","Owned"),200);
        assertEquals(1,call(get("/api/v1/customers/me/buildings"),a,null,200).size());assertEquals(0,call(get("/api/v1/customers/me/buildings"),b,null,200).size());
        call(post("/api/v1/customers/me/buildings"),a,Map.of("buildingName","Spoof","customerProfileId",customers.findByUserId(b.getId()).orElseThrow().id),400);
        JsonNode lift=call(post("/api/v1/lifts"),admin,Map.of("buildingId",building.get("id").asLong(),"name","Lift","healthScore",100),200);assertEquals(100,lift.get("healthScore").asInt());
        assertEquals(1,call(get("/api/v1/lifts?status=ACTIVE&page=0&size=1"),admin,null,200).size());
        call(get("/api/v1/lifts?page=-1"),admin,null,400);call(get("/api/v1/amc-contracts?size=101"),admin,null,400);
        assertEquals(1,call(get("/api/v1/customers/me/lifts"),a,null,200).size());assertEquals(0,call(get("/api/v1/customers/me/lifts"),b,null,200).size());
        call(post("/api/v1/lifts"),admin,Map.of("buildingId",building.get("id").asLong(),"name","Invalid","healthScore",101),400);
        call(post("/api/v1/service-requests"),a,Map.of("liftId",lift.get("id").asLong(),"title","Issue","description","Details","serviceType","BREAKDOWN"),200);
        assertEquals(1,call(get("/api/v1/customers/me/service-requests"),a,null,200).get("items").size());assertEquals(0,call(get("/api/v1/customers/me/service-requests"),b,null,200).get("items").size());
        assertTrue(call(get("/api/v1/admin/dashboard/summary"),admin,null,200).get("totalRequests").asLong()>0);call(get("/api/v1/admin/dashboard/summary"),a,null,403);
        call(get("/api/v1/customers/me"),admin,null,403);
    }
    JsonNode docs()throws Exception{return json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    JsonNode resolve(JsonNode doc,JsonNode n){return n.has("$ref")?doc.at(n.get("$ref").asText().substring(1)):n;}
    JsonNode schema(JsonNode doc,String path,String method,boolean request){JsonNode op=doc.get("paths").get(path).get(method);JsonNode c=request?op.get("requestBody"):op.at("/responses/200");return resolve(doc,c.get("content").elements().next().get("schema"));}
    Set<String> values(JsonNode n){Set<String>s=new HashSet<>();n.forEach(v->s.add(v.asText()));return s;}
    @Test void authOpenApiIsTypedAndOtpInputsAreDistinct()throws Exception{
        JsonNode doc=docs(),reg=schema(doc,"/api/v1/auth/register","post",true);assertFalse(reg.get("properties").has("role"));assertTrue(reg.at("/properties/password/writeOnly").asBoolean());
        for(String p:List.of("admin","technician")){JsonNode input=schema(doc,"/api/v1/auth/login/"+p,"post",true);assertTrue(input.get("properties").has("email"));assertFalse(input.get("properties").has("identity"));}
        JsonNode send=schema(doc,"/api/v1/auth/otp/send","post",true),verify=schema(doc,"/api/v1/auth/otp/verify","post",true);assertEquals(1,send.get("properties").size());assertTrue(send.get("properties").has("phone"));assertEquals(Set.of("phone","otp","requestId"),values(verify.get("required")));
        for(String p:List.of("register","login/customer","login/admin","login/technician","otp/verify")){JsonNode response=schema(doc,"/api/v1/auth/"+p,"post",false);JsonNode data=resolve(doc,response.at("/properties/data"));for(String f:List.of("userId","role","accessToken","refreshToken","customerProfile","technicianProfile"))assertTrue(data.get("properties").has(f));}
        JsonNode me=resolve(doc,schema(doc,"/api/v1/me","get",false).at("/properties/data"));assertTrue(me.get("properties").has("role"));assertTrue(me.get("properties").has("customerProfile"));
        JsonNode otp=resolve(doc,schema(doc,"/api/v1/auth/otp/send","post",false).at("/properties/data"));assertTrue(otp.get("properties").has("requestId"));assertTrue(otp.get("properties").has("expiresAt"));
        assertTrue(resolve(doc,schema(doc,"/api/v1/auth/refresh","post",false).at("/properties/data")).get("properties").has("refreshToken"));
    }
    @Test void domainConstraintsAndEveryOperationEnvelopeAreFrozen()throws Exception{
        JsonNode doc=docs(),schemas=doc.at("/components/schemas");
        assertFalse(schemas.toString().contains("passwordHash"));assertFalse(schemas.toString().contains("otpHash"));assertFalse(schemas.toString().contains("tokenHash"));
        schemas.forEach(s->{if(s.path("properties").has("password"))assertTrue(s.at("/properties/password/writeOnly").asBoolean());});
        for(String name:List.of("LiftWrite","LiftView")){JsonNode health=schemas.get(name).at("/properties/healthScore");assertEquals("integer",health.get("type").asText());assertEquals(0,health.get("minimum").asInt());assertEquals(100,health.get("maximum").asInt());}
        assertEquals(Set.of("ADMIN","TECHNICIAN"),values(schemas.get("StaffResponse").at("/properties/role/enum")));
        assertEquals(Set.of("IN_APP"),values(schemas.get("NotificationCreateRequest").at("/properties/channel/enum")));
        assertTrue(schemas.get("CreateRequest").at("/properties/customerProfileId/description").asText().contains("CUSTOMER"));
        assertTrue(schemas.get("CreateRequest").at("/properties/internalAdminNotes/description").asText().contains("rejected"));
        assertTrue(schemas.get("Detail").at("/properties/activeAssignment/nullable").asBoolean());assertTrue(schemas.get("Detail").at("/properties/report/nullable").asBoolean());
        assertEquals(Set.of("PENDING","ASSIGNED","ACCEPTED","ON_THE_WAY","REACHED_SITE","DIAGNOSIS","REPAIR_IN_PROGRESS","WAITING_FOR_PARTS","TESTING","COMPLETED","CANCELLED"),values(schemas.get("StatusRequest").at("/properties/toStatus/enum")));
        assertEquals(Set.of("LOW","MEDIUM","HIGH","EMERGENCY"),values(schemas.get("CreateRequest").at("/properties/priority/enum")));
        assertEquals(Set.of("ROUTINE_MAINTENANCE","BREAKDOWN","EMERGENCY","INSPECTION","INSTALLATION","MODERNIZATION"),values(schemas.get("CreateRequest").at("/properties/serviceType/enum")));
        assertEquals(Set.of("ASSIGNED","ACCEPTED","REJECTED","RELEASED","COMPLETED"),values(schemas.get("AssignmentView").at("/properties/status/enum")));
        Set<String> ids=new HashSet<>();doc.get("paths").fields().forEachRemaining(e->{if(e.getKey().startsWith("/api/v1"))e.getValue().forEach(op->{
            String operationId=op.get("operationId").asText();assertTrue(ids.add(operationId));for(String code:List.of("400","401","403","404","409"))assertTrue(op.get("responses").get(code).toString().contains("ApiErrorResponse"));
            if("serviceRequestAttachmentDownload".equals(operationId))return;
            JsonNode success=op.get("responses").get("200");assertNotNull(success);
            JsonNode envelope=resolve(doc,success.get("content").elements().next().get("schema"));
            JsonNode data=resolve(doc,envelope.at("/properties/data"));
            assertTrue(data.has("properties")||data.has("items")||data.path("description").asText().contains("Always null"),operationId);
        });});
    }
}
