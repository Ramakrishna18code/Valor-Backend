package com.valor.auth;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:openapi_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc @ActiveProfiles("test")
class StaffOpenApiTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;
    JsonNode docs() throws Exception {return mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    JsonNode resolve(JsonNode doc,JsonNode schema) {return schema.has("$ref")?doc.at(schema.get("$ref").asText().substring(1)):schema;}
    JsonNode content(JsonNode node) {return node.get("content").elements().next().get("schema");}
    Set<String> fields(JsonNode node) {Set<String> names=new HashSet<>();node.get("properties").fieldNames().forEachRemaining(names::add);return names;}
    @Test void staffSchemasAreDistinctAndContainOnlyRuntimeFields() throws Exception {
        JsonNode doc=docs(), post=doc.at("/paths/~1api~1v1~1admin~1users/post");
        JsonNode request=content(post.get("requestBody"));assertTrue(request.toString().contains("StaffCreateRequest"));
        assertEquals(Set.of("email","password","role","employeeId","assignedArea","specialization","availabilityStatus"),fields(resolve(doc,request)));
        for(JsonNode op:List.of(post,doc.at("/paths/~1api~1v1~1admin~1users~1{userId}/delete"))) {
            JsonNode response=content(op.at("/responses/200"));
            assertFalse(response.toString().contains("Notification"));
            JsonNode data=resolve(doc,response).at("/properties/data");assertTrue(data.toString().contains("StaffResponse"));
            assertEquals(Set.of("userId","email","role","active","technicianProfileId","employeeId","assignedArea","specialization","availabilityStatus"),fields(resolve(doc,data)));
        }
        assertEquals("createStaff",post.get("operationId").asText());
        assertEquals("deactivateStaff",doc.at("/paths/~1api~1v1~1admin~1users~1{userId}/delete/operationId").asText());
    }
    @Test void staffRequestConstraintsExcludePrivilegedRolesAndProtectPassword() throws Exception {
        JsonNode doc=docs();
        JsonNode request=resolve(doc,content(doc.at("/paths/~1api~1v1~1admin~1users/post/requestBody")));
        JsonNode role=request.at("/properties/role");
        Set<String> roles=new HashSet<>();role.get("enum").forEach(v->roles.add(v.asText()));
        assertEquals(Set.of("ADMIN","TECHNICIAN"),roles);
        assertEquals("TECHNICIAN",role.get("example").asText());
        assertTrue(request.at("/properties/password/writeOnly").asBoolean());
        assertEquals("password",request.at("/properties/password/format").asText());
        Set<String> availability=new HashSet<>();request.at("/properties/availabilityStatus/enum").forEach(v->availability.add(v.asText()));
        assertEquals(Set.of("AVAILABLE","BUSY","OFF_DUTY","ON_LEAVE"),availability);
        for(String field:List.of("employeeId","assignedArea","specialization","availabilityStatus")) {
            String description=request.at("/properties/"+field+"/description").asText();
            assertTrue(description.contains("Required for TECHNICIAN"));assertTrue(description.contains("inapplicable for ADMIN"));
        }
        for(JsonNode op:List.of(doc.at("/paths/~1api~1v1~1admin~1users/post"),doc.at("/paths/~1api~1v1~1admin~1users~1{userId}/delete"))) {
            JsonNode envelope=resolve(doc,content(op.at("/responses/200")));
            assertFalse(fields(envelope).contains("password"));
            assertFalse(fields(resolve(doc,envelope.at("/properties/data"))).contains("password"));
        }
    }
    @Test void notificationSchemasRemainCorrectAndOperationIdsAreUnique() throws Exception {
        JsonNode doc=docs(),post=doc.at("/paths/~1api~1v1~1notifications/post");
        assertTrue(content(post.get("requestBody")).toString().contains("NotificationCreateRequest"));
        for(JsonNode op:List.of(post,doc.at("/paths/~1api~1v1~1notifications~1{id}~1read/put"))) {
            JsonNode data=resolve(doc,content(op.at("/responses/200"))).at("/properties/data");
            assertTrue(data.toString().contains("NotificationResponse"));assertFalse(data.toString().contains("Staff"));
        }
        JsonNode page=resolve(doc,resolve(doc,content(doc.at("/paths/~1api~1v1~1notifications/get/responses/200"))).at("/properties/data"));
        assertTrue(page.at("/properties/items/items/$ref").asText().contains("NotificationResponse"));
        assertEquals("createNotification",post.get("operationId").asText());
        assertEquals("getNotifications",doc.at("/paths/~1api~1v1~1notifications/get/operationId").asText());
        assertEquals("markNotificationRead",doc.at("/paths/~1api~1v1~1notifications~1{id}~1read/put/operationId").asText());
        Set<String> ids=new HashSet<>();doc.get("paths").forEach(path->path.forEach(op->{if(op.has("operationId"))assertTrue(ids.add(op.get("operationId").asText()));}));
    }
}
