package com.valor.parts;

import com.valor.auth.PartsCatalogSecurityTestBeans;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.data.domain.PageImpl;
import com.valor.auth.User;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PartsController.class, properties = "app.cors.allowed-origins=http://localhost")
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(PartsCatalogSecurityTestBeans.class)
@ContextConfiguration(classes = {PartsController.class, PartsCatalogSecurityTestBeans.class})
class PartsCatalogHttpSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired PartsCatalogSecurityTestBeans.SyntheticAuth syntheticAuth;
    @MockBean PartsService service;

    @org.junit.jupiter.api.BeforeEach
    void syntheticUsers() {
        long id = 1L;
        for (String token : List.of("customer", "technician", "admin", "superadmin", "nurse")) {
            User user = new User();
            user.setActive(true);
            user.setLocked(false);
            user.setRole(switch (token) {
                case "customer" -> com.valor.auth.Role.CUSTOMER;
                case "technician" -> com.valor.auth.Role.TECHNICIAN;
                case "admin" -> com.valor.auth.Role.ADMIN;
                case "superadmin" -> com.valor.auth.Role.SUPER_ADMIN;
                default -> null;
            });
            user.setEmail(token + "@test.invalid");
            syntheticAuth.configure(token, id, user);
            id++;
        }
    }

    @Test
    void anonymousCatalogRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/parts/categories")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/parts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/parts/11")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerCatalogJsonOmitsInternalFields() throws Exception {
        when(service.categories(false)).thenReturn(List.of(new PartsDtos.CategoryView(3L, "Filters", "", true)));
        when(service.catalog(isNull(), isNull(), eq(false), any())).thenAnswer(inv -> new PageImpl<>(List.of(
                new PartsDtos.CustomerItemView(11L, 3L, "Filters", "Filter", "Replacement", "piece", true, true))));
        when(service.item(11L)).thenReturn(new PartsDtos.CustomerItemView(11L, 3L, "Filters", "Filter", "Replacement", "piece", true, true));

        mvc.perform(get("/api/v1/parts/categories").header("Authorization", "Bearer customer"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/parts").header("Authorization", "Bearer customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].sku").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].compatibilityMetadata").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].quantityOnHand").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].reorderThreshold").doesNotExist());
        mvc.perform(get("/api/v1/parts/11").header("Authorization", "Bearer customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").doesNotExist())
                .andExpect(jsonPath("$.data.quantityOnHand").doesNotExist());
    }

    @Test
    void technicianCatalogRetainsUsefulFieldsButOmitsStockQuantities() throws Exception {
        when(service.categories(false)).thenReturn(List.of());
        when(service.catalog(isNull(), isNull(), eq(false), any())).thenAnswer(inv -> new PageImpl<>(List.of(
                new PartsDtos.TechnicianItemView(11L, 3L, "Filters", "Filter", "SKU-11", "Replacement", "piece", true, "compat", true))));
        when(service.item(11L)).thenReturn(new PartsDtos.TechnicianItemView(11L, 3L, "Filters", "Filter", "SKU-11", "Replacement", "piece", true, "compat", true));

        mvc.perform(get("/api/v1/parts/categories").header("Authorization", "Bearer technician"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/parts").header("Authorization", "Bearer technician"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].sku").value("SKU-11"))
                .andExpect(jsonPath("$.data.content[0].compatibilityMetadata").value("compat"))
                .andExpect(jsonPath("$.data.content[0].quantityOnHand").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].reorderThreshold").doesNotExist());
        mvc.perform(get("/api/v1/parts/11").header("Authorization", "Bearer technician"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantityOnHand").doesNotExist());
    }

    @Test
    void adminsRetainFullInventoryFieldsAndAdminPathIsProtected() throws Exception {
        PartsDtos.ItemView full = new PartsDtos.ItemView(11L, 3L, "Filters", "Filter", "SKU-11", "Replacement", "piece", 7, 2, true, "compat", true);
        when(service.categories(true)).thenReturn(List.of());
        when(service.catalog(isNull(), isNull(), eq(true), any())).thenAnswer(inv -> new PageImpl<>(List.of(full)));
        when(service.adminItem(11L)).thenReturn(full);

        for (String role : List.of("ADMIN", "SUPER_ADMIN")) {
            String token = role.equals("ADMIN") ? "admin" : "superadmin";
            mvc.perform(get("/api/v1/admin/parts").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].quantityOnHand").value(7))
                    .andExpect(jsonPath("$.data.content[0].reorderThreshold").value(2))
                    .andExpect(jsonPath("$.data.content[0].sku").value("SKU-11"));
            mvc.perform(get("/api/v1/admin/parts/11").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.quantityOnHand").value(7));
        }
        mvc.perform(get("/api/v1/admin/parts").header("Authorization", "Bearer customer"))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogMatcherDoesNotAllowUnsupportedAuthenticatedRole() throws Exception {
        mvc.perform(get("/api/v1/parts").header("Authorization", "Bearer nurse"))
                .andExpect(status().isForbidden());
    }

}
