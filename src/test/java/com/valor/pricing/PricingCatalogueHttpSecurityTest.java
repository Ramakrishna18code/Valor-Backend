package com.valor.pricing;

import com.valor.auth.AssetIdentityAccess;
import com.valor.auth.PartsCatalogSecurityTestBeans;
import com.valor.auth.User;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PricingCatalogueController.class, properties = "app.cors.allowed-origins=http://localhost")
@AutoConfigureMockMvc
@Import(PartsCatalogSecurityTestBeans.class)
@ContextConfiguration(classes = {PricingCatalogueController.class, PartsCatalogSecurityTestBeans.class})
class PricingCatalogueHttpSecurityTest {
    private static final String PATH = "/api/v1/admin/pricing-catalogue";

    @Autowired MockMvc mvc;
    @Autowired PartsCatalogSecurityTestBeans.SyntheticAuth syntheticAuth;
    @MockBean PricingCatalogue catalogue;
    @MockBean AssetIdentityAccess identities;

    @BeforeEach
    void syntheticUsersAndCatalogue() {
        long id = 1L;
        for (String token : List.of("customer", "technician", "admin", "superadmin")) {
            User user = new User();
            user.setActive(true);
            user.setLocked(false);
            user.setRole(switch (token) {
                case "customer" -> com.valor.auth.Role.CUSTOMER;
                case "technician" -> com.valor.auth.Role.TECHNICIAN;
                case "admin" -> com.valor.auth.Role.ADMIN;
                case "superadmin" -> com.valor.auth.Role.SUPER_ADMIN;
                default -> throw new IllegalStateException(token);
            });
            user.setEmail(token + "@test.invalid");
            syntheticAuth.configure(token, id++, user);
        }
        doNothing().when(identities).requireAdmin();
        PricingCatalogue.Item item = new PricingCatalogue.Item(
                "TEST_FIXED", "ONE_TIME_SERVICE", "Test inspection", "Validated test catalogue item",
                PricingCatalogue.PricingType.FIXED, "PER_JOB", null, null, null,
                new BigDecimal("999"), null, null, false, "REQUIRES_OWNER_CONFIRMATION");
        when(catalogue.read()).thenReturn(new PricingCatalogue.CatalogueDocument(
                1, "INR", "REQUIRES_BUSINESS_FINANCE_APPROVAL", List.of(item)));
    }

    @Test
    void anonymousCustomerAndTechnicianRequestsAreRejected() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("Authorization", "Bearer customer")).andExpect(status().isForbidden());
        mvc.perform(get(PATH).header("Authorization", "Bearer technician")).andExpect(status().isForbidden());
    }

    @Test
    void adminAndSuperAdminReceiveValidatedReadOnlyCatalogue() throws Exception {
        for (String token : List.of("admin", "superadmin")) {
            mvc.perform(get(PATH).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.schemaVersion").value(1))
                    .andExpect(jsonPath("$.data.currency").value("INR"))
                    .andExpect(jsonPath("$.data.items[0].code").value("TEST_FIXED"))
                    .andExpect(jsonPath("$.data.items[0].reviewStatus").value("REQUIRES_OWNER_CONFIRMATION"));
        }
    }

    @Test
    void noMutationHandlerExistsAndGenericAdminFallbackRemainsSuperAdminOnly() throws Exception {
        mvc.perform(post(PATH).header("Authorization", "Bearer admin")).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(PATH).header("Authorization", "Bearer superadmin")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/v1/admin/not-an-explicitly-authorized-route").header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/not-an-explicitly-authorized-route").header("Authorization", "Bearer superadmin"))
                .andExpect(status().isNotFound());
    }
}
