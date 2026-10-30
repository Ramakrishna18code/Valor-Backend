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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CustomerPricingCatalogueController.class, properties = "app.cors.allowed-origins=http://localhost")
@AutoConfigureMockMvc
@Import(PartsCatalogSecurityTestBeans.class)
@ContextConfiguration(classes = {CustomerPricingCatalogueController.class, PartsCatalogSecurityTestBeans.class})
class CustomerPricingCatalogueHttpSecurityTest {
    private static final String PATH = "/api/v1/customer/pricing-catalogue";

    @Autowired MockMvc mvc;
    @Autowired PartsCatalogSecurityTestBeans.SyntheticAuth syntheticAuth;
    @MockBean CustomerPricingCatalogueService service;
    @MockBean AssetIdentityAccess identities;

    @BeforeEach
    void usersAndResponse() {
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
        doNothing().when(identities).requireActiveCustomerUser(org.mockito.ArgumentMatchers.any(User.class));
        when(identities.actor()).thenReturn(new User());
        when(service.read()).thenReturn(new CustomerPricingCatalogueResponse(1, "INR", "Reference disclaimer", "Applicable taxes pending",
                List.of(new CustomerPricingCatalogueResponse.Item("STANDARD_BREAKDOWN_CALLOUT", "EMERGENCY_SERVICE", "Callout", "Reference", "REFERENCE_FIXED", new BigDecimal("2000"), false, "PER_JOB"))));
    }

    @Test
    void onlyAuthenticatedCustomerCanRead() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("Authorization", "Bearer technician")).andExpect(status().isForbidden());
        mvc.perform(get(PATH).header("Authorization", "Bearer admin")).andExpect(status().isForbidden());
        mvc.perform(get(PATH).header("Authorization", "Bearer superadmin")).andExpect(status().isForbidden());
        mvc.perform(get(PATH).header("Authorization", "Bearer customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("INR"))
                .andExpect(jsonPath("$.data.items[0].referenceAmount").value(2000));
    }

    @Test
    void customerRouteHasNoMutationHandlers() throws Exception {
        for (var request : List.of(post(PATH), put(PATH), patch(PATH), delete(PATH))) {
            mvc.perform(request.header("Authorization", "Bearer customer")).andExpect(status().isMethodNotAllowed());
        }
    }
}
