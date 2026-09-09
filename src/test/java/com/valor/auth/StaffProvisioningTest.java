package com.valor.auth;

import com.fasterxml.jackson.databind.*;
import java.util.*;
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

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:staff_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc @ActiveProfiles("test")
class StaffProvisioningTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepo users;
    @Autowired TechRepo techs; @Autowired CustomerRepo customers; @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthService auth; @Autowired TokenRepo tokens;
    static final String PASSWORD="test-only-staff-password";
    User actor(Role role) { User u=new User();u.setEmail(UUID.randomUUID()+"@example.test");u.setRole(role);return users.saveAndFlush(u); }
    Map<String,Object> input(Role role) { return new HashMap<>(Map.of("email",UUID.randomUUID()+"@example.test","password",PASSWORD,"role",role.name())); }
    JsonNode call(MockHttpServletRequestBuilder request,User actor,Object body,int status) throws Exception {
        if(actor!=null)request.header("Authorization","Bearer "+jwt.issue(actor));
        if(body!=null)request.contentType("application/json").content(json.writeValueAsString(body));
        String response=mvc.perform(request).andExpect(status().is(status)).andExpect(jsonPath("$.success").value(status<400))
          .andExpect(jsonPath("$.status").value(status)).andReturn().getResponse().getContentAsString();
        for(String secret:List.of(PASSWORD,"passwordHash","tokenHash","otpHash","stackTrace","org.hibernate","com.valor"))assertFalse(response.contains(secret));
        return json.readTree(response).get("data");
    }
    JsonNode create(User actor,Map<String,Object> body,int status)throws Exception{return call(post("/api/v1/admin/users"),actor,body,status);}
    @Test void adminHasNoProfileAndBcryptOnly()throws Exception{
        var body=input(Role.ADMIN);long id=create(actor(Role.SUPER_ADMIN),body,200).get("userId").asLong();
        User u=users.findById(id).orElseThrow();assertEquals(Role.ADMIN,u.getRole());assertTrue(u.isActive());assertFalse(u.isLocked());assertEquals(0,u.getFailedLoginAttempts());
        assertNotEquals(PASSWORD,u.getPasswordHash());assertTrue(encoder.matches(PASSWORD,u.getPasswordHash()));assertTrue(u.getPasswordHash().startsWith("$2"));
        assertTrue(techs.findByUserId(id).isEmpty());assertTrue(customers.findByUserId(id).isEmpty());
    }
    @Test void technicianCreatedWithProfile()throws Exception{
        var body=input(Role.TECHNICIAN);body.put("employeeId",UUID.randomUUID().toString());body.put("assignedArea","North");body.put("specialization","Electrical");body.put("availabilityStatus","OFF_DUTY");
        long id=create(actor(Role.SUPER_ADMIN),body,200).get("userId").asLong();var p=techs.findByUserId(id).orElseThrow();
        assertTrue(p.active);assertEquals("North",p.assignedArea);assertEquals("Electrical",p.specialization);assertEquals("OFF_DUTY",p.availabilityStatus);assertTrue(customers.findByUserId(id).isEmpty());
    }
    @Test void emailNormalizedAndDuplicateRejected()throws Exception{
        var a=actor(Role.SUPER_ADMIN);var b=input(Role.ADMIN);String email=b.get("email").toString();b.put("email","  "+email.toUpperCase(Locale.ROOT)+"  ");
        assertEquals(email,create(a,b,200).get("email").asText());b.put("email",email);create(a,b,409);
    }
    @Test void duplicateEmployeeRollsBackNewUser()throws Exception{
        var a=actor(Role.SUPER_ADMIN);String employee=UUID.randomUUID().toString();var b=input(Role.TECHNICIAN);b.put("employeeId",employee);create(a,b,200);
        var duplicate=input(Role.TECHNICIAN);duplicate.put("employeeId",employee);long before=users.count();create(a,duplicate,409);
        assertEquals(before,users.count());assertTrue(users.findByEmail(duplicate.get("email").toString()).isEmpty());
    }
    @Test void prohibitedRolesRejected()throws Exception{var a=actor(Role.SUPER_ADMIN);for(Role r:List.of(Role.CUSTOMER,Role.SUPER_ADMIN))create(a,input(r),400);}
    @Test void onlySuperAdminCanCreateOrDeactivate()throws Exception{
        var target=actor(Role.ADMIN);for(Role r:List.of(Role.ADMIN,Role.CUSTOMER,Role.TECHNICIAN)){var a=actor(r);create(a,input(Role.ADMIN),403);call(delete("/api/v1/admin/users/"+target.getId()),a,null,403);}
        create(null,input(Role.ADMIN),401);call(delete("/api/v1/admin/users/"+target.getId()),null,null,401);
    }
    @Test void deactivationRetainsRowsProfilesAndIsIdempotent()throws Exception{
        var a=actor(Role.SUPER_ADMIN);for(Role role:List.of(Role.ADMIN,Role.TECHNICIAN)){
            var body=input(role);long id=create(a,body,200).get("userId").asLong();User old=users.findById(id).orElseThrow();String hash=old.getPasswordHash();long count=users.count();
            call(delete("/api/v1/admin/users/"+id),a,null,200);call(delete("/api/v1/admin/users/"+id),a,null,200);
            assertEquals(count,users.count());assertFalse(users.findById(id).orElseThrow().isActive());assertEquals(hash,users.findById(id).orElseThrow().getPasswordHash());
            if(role==Role.TECHNICIAN)assertFalse(techs.findByUserId(id).orElseThrow().active);
            call(post("/api/v1/auth/login/"+(role==Role.ADMIN?"admin":"technician")),null,Map.of("identity",body.get("email"),"password",PASSWORD),400);
            call(get("/api/v1/me"),old,null,401);
        }
    }
    @Test void selfCustomerAndOtherSuperAdminCannotBeDeactivated()throws Exception{
        var a=actor(Role.SUPER_ADMIN);for(User target:List.of(a,actor(Role.CUSTOMER),actor(Role.SUPER_ADMIN))){call(delete("/api/v1/admin/users/"+target.getId()),a,null,400);assertTrue(users.findById(target.getId()).orElseThrow().isActive());}
    }
    @Test void deactivationRetainsRefreshRowsAndPreventsNewSessions()throws Exception{
        var a=actor(Role.SUPER_ADMIN);long id=create(a,input(Role.TECHNICIAN),200).get("userId").asLong();
        User staff=users.findById(id).orElseThrow();String refresh=auth.session(staff)[1];
        long count=tokens.count();
        call(delete("/api/v1/admin/users/"+id),a,null,200);
        assertEquals(count,tokens.count());
        call(post("/api/v1/auth/refresh"),null,Map.of("refreshToken",refresh),400);
        assertEquals(count,tokens.count());
        assertNull(tokens.findByTokenHash(AuthService.hash(refresh)).orElseThrow().getRevokedAt());
    }
    @Test void invalidFieldsAndMissingStaffHaveSafeErrors()throws Exception{
        var a=actor(Role.SUPER_ADMIN);var b=input(Role.TECHNICIAN);b.put("availabilityStatus","INVALID");create(a,b,400);
        b=input(Role.ADMIN);b.put("email","invalid");create(a,b,400);b=input(Role.ADMIN);b.put("password","");create(a,b,400);
        b=input(Role.ADMIN);b.put("passwordHash","forbidden");create(a,b,400);
        call(delete("/api/v1/admin/users/9223372036854775807"),a,null,404);call(delete("/api/v1/admin/users/invalid"),a,null,400);
    }
}
