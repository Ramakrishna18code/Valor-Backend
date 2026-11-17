package com.valor.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
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

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:website_enquiries;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class WebsiteEnquiryTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepo users;
    @Autowired JwtService jwt;

    User user(Role role) {
        var actor = new User(); actor.setRole(role); actor.setEmail(UUID.randomUUID()+"@example.test");
        return users.saveAndFlush(actor);
    }
    String token(User actor) { return "Bearer " + jwt.issue(actor); }

    @Test void publicSubmissionIsPersistedAndOnlyAdminsCanReadOrUpdate() throws Exception {
        String name = "Website-" + UUID.randomUUID();
        String body = "{\"source\":\"SERVICE\",\"name\":\"" + name + "\",\"phone\":\"+91 98765 43210\",\"serviceType\":\"Repair\"}";
        var created = mvc.perform(post("/api/v1/public/website-enquiries").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reference").isNotEmpty()).andReturn().getResponse().getContentAsString();
        String reference = json.readTree(created).path("data").path("reference").asText();
        mvc.perform(get("/api/v1/admin/website-enquiries")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/website-enquiries").header("Authorization",token(user(Role.CUSTOMER))))
                .andExpect(status().isForbidden());
        var list = mvc.perform(get("/api/v1/admin/website-enquiries?status=NEW").header("Authorization",token(user(Role.ADMIN))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode items = json.readTree(list).path("data").path("items");
        JsonNode row = null;
        for (JsonNode item : items) if (reference.equals(item.path("reference").asText())) row = item;
        assertNotNull(row);
        assertEquals(name, row.path("name").asText());
        long id = row.path("id").asLong();
        mvc.perform(patch("/api/v1/admin/website-enquiries/{id}/status",id).header("Authorization",token(user(Role.TECHNICIAN)))
                .contentType("application/json").content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/admin/website-enquiries/{id}/status",id).header("Authorization",token(user(Role.SUPER_ADMIN)))
                .contentType("application/json").content("{\"status\":\"CONTACTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONTACTED"));
    }

    @Test void invalidPublicSubmissionsAreRejected() throws Exception {
        for (String body : new String[]{"{}", "{\"source\":\"SERVICE\",\"name\":\"Test\",\"phone\":\"123\"}",
                "{\"source\":\"ADMIN\",\"name\":\"Test\",\"phone\":\"9876543210\"}"}) {
            mvc.perform(post("/api/v1/public/website-enquiries").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }
}
