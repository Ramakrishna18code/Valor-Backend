package com.valor.auth;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:directories;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AdminDirectoriesTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepo users;
    @Autowired CustomerRepo customers; @Autowired TechRepo techs; @Autowired JwtService jwt;
    User user(Role role) { var u=new User();u.setRole(role);u.setEmail(UUID.randomUUID()+"@example.test");return users.saveAndFlush(u); }
    TechnicianProfile technician(String marker) {var p=new TechnicianProfile();p.user=user(Role.TECHNICIAN);p.employeeId=marker;p.assignedArea="North";p.specialization="Electrical";return techs.saveAndFlush(p);}
    CustomerProfile customer(String marker) {var p=new CustomerProfile();p.user=user(Role.CUSTOMER);p.fullName=marker;return customers.saveAndFlush(p);}
    JsonNode call(String path,User actor,int expected) throws Exception {
        var request=get("/api/v1/admin/"+path);if(actor!=null)request.header("Authorization","Bearer "+jwt.issue(actor));
        String body=mvc.perform(request).andExpect(status().is(expected)).andExpect(jsonPath("$.success").value(expected==200))
            .andExpect(jsonPath("$.status").value(expected)).andReturn().getResponse().getContentAsString();
        for(String secret:List.of("password","passwordHash","otpHash","tokenHash","accessToken","refreshToken","stackTrace"))assertFalse(body.contains(secret));
        return json.readTree(body).get("data");
    }
    @Test void bothAdministratorRolesAllowed() throws Exception {
        for(Role role:List.of(Role.ADMIN,Role.SUPER_ADMIN))for(String path:List.of("technicians","customers"))call(path,user(role),200);
    }
    @Test void otherRolesForbiddenAndAnonymousUnauthorized() throws Exception {
        for(String path:List.of("technicians","customers")) {
            call(path,null,401);for(Role role:List.of(Role.CUSTOMER,Role.TECHNICIAN))call(path,user(role),403);
        }
    }
    @Test void techniciansProvideStablePagedAssignmentIds() throws Exception {
        var a=user(Role.ADMIN);String key=UUID.randomUUID().toString();var first=technician(key+"-1");var second=technician(key+"-2");
        var page=call("technicians?q="+key+"&size=1",a,200);
        assertEquals(2,page.get("totalElements").asInt());assertEquals(2,page.get("totalPages").asInt());assertEquals(0,page.get("page").asInt());
        var row=page.get("items").get(0);assertEquals(first.id,row.get("technicianProfileId").asLong());assertEquals(first.user.getId(),row.get("userId").asLong());
        assertEquals("AVAILABLE",row.get("availabilityStatus").asText());assertTrue(row.get("active").asBoolean());
        assertEquals(second.id,call("technicians?q="+key+"&size=1&page=1",a,200).get("items").get(0).get("technicianProfileId").asLong());
        assertEquals(page,call("technicians?q="+key+"&size=1",a,200));
    }
    @Test void customersProvideStablePagedCreationIdsAndSafeFields() throws Exception {
        var a=user(Role.ADMIN);String key=UUID.randomUUID().toString();var first=customer(key+"-1");var second=customer(key+"-2");
        var row=call("customers?q="+key+"&size=1",a,200).get("items").get(0);
        assertEquals(first.id,row.get("customerProfileId").asLong());assertEquals(first.user.getId(),row.get("userId").asLong());assertEquals(first.fullName,row.get("fullName").asText());
        Set<String> fields=new HashSet<>();row.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("userId","customerProfileId","fullName","email","phone","active","status"),fields);
        assertEquals(second.id,call("customers?q="+key+"&size=1&page=1",a,200).get("items").get(0).get("customerProfileId").asLong());
    }
    @Test void searchesAllSupportedFieldsCaseInsensitively() throws Exception {
        var a=user(Role.ADMIN);String key=UUID.randomUUID().toString();var t=technician(key);t.assignedArea=key+"area";t.specialization=key+"skill";techs.flush();
        for(String value:List.of(key.toUpperCase(Locale.ROOT),t.user.getEmail(),t.assignedArea,t.specialization))
            assertEquals(t.id,call("technicians?q="+value,a,200).get("items").get(0).get("technicianProfileId").asLong());
        var c=customer(key);c.user.setPhone("+919123456789");users.flush();
        for(String value:List.of(key.toUpperCase(Locale.ROOT),c.user.getEmail(),"919123456789"))
            assertEquals(c.id,call("customers?q="+value,a,200).get("items").get(0).get("customerProfileId").asLong());
    }
    @Test void activeCombinesUserAndProfileFlagsForBothDirectories() throws Exception {
        var a=user(Role.ADMIN);String key=UUID.randomUUID().toString();var t1=technician(key+"a");var t2=technician(key+"b");technician(key+"c");t1.active=false;t2.user.setActive(false);
        var c1=customer(key+"a");var c2=customer(key+"b");customer(key+"c");c1.active=false;c2.user.setActive(false);users.flush();
        for(String path:List.of("customers","technicians")) {
            assertEquals(3,call(path+"?q="+key,a,200).get("totalElements").asInt());
            assertEquals(1,call(path+"?q="+key+"&active=true",a,200).get("totalElements").asInt());
            var inactive=call(path+"?q="+key+"&active=false",a,200);assertEquals(2,inactive.get("totalElements").asInt());
            inactive.get("items").forEach(row->assertFalse(row.get("active").asBoolean()));
        }
    }
    @Test void emptyAndOutOfRangePagesAreValid() throws Exception {
        var a=user(Role.ADMIN);for(String path:List.of("technicians","customers")) {
            var empty=call(path+"?q=never-"+UUID.randomUUID(),a,200);assertEquals(0,empty.get("items").size());assertEquals(0,empty.get("totalElements").asInt());assertEquals(20,empty.get("size").asInt());
        }
        String key=UUID.randomUUID().toString();customer(key);var page=call("customers?q="+key+"&page=2&size=1",a,200);assertEquals(0,page.get("items").size());assertEquals(1,page.get("totalElements").asInt());
    }
    @Test void invalidPagingAndFiltersReturnSafe400() throws Exception {
        var a=user(Role.ADMIN);for(String path:List.of("technicians","customers"))for(String query:List.of("page=-1","size=0","size=101","page=2147483647&size=100","page=bad","active=bad","q="+"x".repeat(255)))call(path+"?"+query,a,400);
    }
    @Test void searchTreatsWildcardsLiterallyAndTrims() throws Exception {
        var a=user(Role.ADMIN);String key=UUID.randomUUID().toString();customer(key+"_literal");customer(key+"Xliteral");
        assertEquals(1,call("customers?q="+key+"_literal",a,200).get("totalElements").asInt());
        assertEquals(2,call("customers?q=  "+key+"  ",a,200).get("totalElements").asInt());
        assertEquals(0,call("customers?q=%",a,200).get("totalElements").asInt());
    }
    @Test void directoryRuleDoesNotOpenStaffWritesToAdmin() throws Exception {
        mvc.perform(post("/api/v1/admin/users").header("Authorization","Bearer "+jwt.issue(user(Role.ADMIN))).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }
    @Test void openApiDocumentsTypedPagesAndUniqueOperations() throws Exception {
        var doc=json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for(String kind:List.of("technicians","customers")) {
            var op=doc.get("paths").get("/api/v1/admin/"+kind).get("get");assertEquals(kind.equals("technicians")?"getAdminTechnicians":"getAdminCustomers",op.get("operationId").asText());
            Set<String> params=new HashSet<>();op.get("parameters").forEach(p->params.add(p.get("name").asText()));assertEquals(Set.of("page","size","q","active"),params);
            var envelope=resolve(doc,op.at("/responses/200/content").elements().next().get("schema"));var page=resolve(doc,envelope.at("/properties/data"));
            var item=resolve(doc,page.at("/properties/items/items"));assertTrue(item.get("properties").has(kind.equals("technicians")?"technicianProfileId":"customerProfileId"));assertFalse(item.toString().contains("password"));
            for(String status:List.of("400","401","403"))assertTrue(op.at("/responses/"+status).toString().contains("ApiErrorResponse"));
        }
        Set<String> ids=new HashSet<>();doc.get("paths").forEach(path->path.forEach(op->{if(op.has("operationId"))assertTrue(ids.add(op.get("operationId").asText()));}));
    }
    JsonNode resolve(JsonNode doc,JsonNode schema) {return schema.has("$ref")?doc.at(schema.get("$ref").asText().substring(1)):schema;}
}
