package com.valor.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:estimate_catalogue;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EstimateCatalogueApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepo users;
    @Autowired JwtService jwt;
    private String token(Role role) {
        User user = new User(); user.setEmail(UUID.randomUUID()+"@example.test"); user.setRole(role);
        return "Bearer " + jwt.issue(users.saveAndFlush(user));
    }
    @Test void onlyAdminsCanWriteAndPublishWhileAppsSeePublishedActiveEntries() throws Exception {
        String admin = token(Role.ADMIN), tech = token(Role.TECHNICIAN), customer = token(Role.CUSTOMER);
        mvc.perform(get("/api/v1/service-estimates")).andExpect(status().isUnauthorized());
        for (String actor : List.of(tech, customer)) {
            mvc.perform(get("/api/v1/admin/service-estimates").header("Authorization", actor)).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/admin/service-estimates").header("Authorization", actor).contentType("application/json").content("{\"version\":0,\"items\":[]}")).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/admin/service-estimates/publish").header("Authorization", actor).contentType("application/json").content("{\"version\":0}")).andExpect(status().isForbidden());
        }
        var draft = json.readTree(mvc.perform(get("/api/v1/admin/service-estimates").header("Authorization", admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        assertTrue(draft.get("items").size() > 30);
        long version = draft.get("version").asLong();
        var active = Map.of("id", "inspection", "category", "Inspection", "name", "General inspection", "min", 999, "active", true);
        var inactive = Map.of("id", "hidden", "category", "Inspection", "name", "Hidden service", "min", 1200, "active", false);
        String write = json.writeValueAsString(Map.of("version", version, "items", List.of(active, inactive)));
        mvc.perform(put("/api/v1/admin/service-estimates").header("Authorization", admin).contentType("application/json").content(write)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/service-estimates").header("Authorization", tech)).andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(put("/api/v1/admin/service-estimates").header("Authorization", admin).contentType("application/json").content(write)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/service-estimates/publish").header("Authorization", admin).contentType("application/json").content(json.writeValueAsString(Map.of("version", version+1)))).andExpect(status().isOk());
        for (String actor : List.of(tech, customer)) mvc.perform(get("/api/v1/service-estimates").header("Authorization", actor)).andExpect(status().isOk()).andExpect(jsonPath("$.data.gstPercent").value(18)).andExpect(jsonPath("$.data.testOnly").value(true)).andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].min").value(999));
        String invalid = json.writeValueAsString(Map.of("version", version+2, "items", List.of(Map.of("id", "bad", "category", "Repair", "name", "Bad range", "min", 3500, "max", 1500, "active", true))));
        mvc.perform(put("/api/v1/admin/service-estimates").header("Authorization", admin).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
    }
}
