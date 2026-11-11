package com.valor.auth;

import com.valor.protection.ProtectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:protection_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProtectionManagementTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepo users;
    @Autowired CustomerRepo customers;
    @Autowired TechRepo technicians;
    @Autowired JwtService jwt;
    @Autowired ProtectionService protection;

    @Test
    void protectionFlowKeepsOwnershipAndInsurerAuthorityExplicit() throws Exception {
        User admin = user(Role.ADMIN);
        User owner = customer("Lift owner");
        User otherCustomer = customer("Other customer");
        User techOwner = technician();
        User otherTechnician = technician();

        JsonNode provider = call("POST", "/api/v1/admin/protection/providers", admin,
                Map.of("name", "Catalog Provider " + UUID.randomUUID(), "active", true), 200);
        call("POST", "/api/v1/admin/protection/providers", owner,
                Map.of("name", "Customer must not manage catalog", "active", true), 403);
        long providerId = provider.get("id").asLong();
        JsonNode liftProduct = call("POST", "/api/v1/admin/protection/products", admin,
                Map.of("providerId", providerId, "name", "Lift cover", "insuredAssetType", "LIFT",
                        "coverageSummary", "Insurer-defined lift cover", "currency", "INR", "active", true), 200);
        JsonNode technicianProduct = call("POST", "/api/v1/admin/protection/products", admin,
                Map.of("providerId", providerId, "name", "Technician cover", "insuredAssetType", "TECHNICIAN",
                        "coverageSummary", "Insurer-defined personal cover", "currency", "INR", "active", true), 200);
        long liftProductId = liftProduct.get("id").asLong();

        JsonNode building = call("POST", "/api/v1/customers/me/buildings", owner,
                Map.of("buildingName", "Protection test building", "latitude", 12.9716, "longitude", 77.5946), 200);
        JsonNode lift = call("POST", "/api/v1/customers/me/lifts", owner,
                Map.of("buildingId", building.get("id").asLong(), "name", "Protection test lift"), 200);
        long liftId = lift.get("id").asLong();
        JsonNode liftPolicy = call("POST", "/api/v1/customers/me/protection/lift-policies", owner,
                Map.of("liftId", liftId, "providerId", providerId, "productId", liftProductId,
                        "insurerPolicyNumber", "EXISTING-LIFT-POLICY-" + UUID.randomUUID(),
                        "coverageStartDate", LocalDate.now().minusDays(1).toString(),
                        "coverageEndDate", LocalDate.now().plusDays(30).toString(), "currency", "INR"), 200);
        long liftPolicyId = liftPolicy.get("id").asLong();
        assertEquals("DECLARED", liftPolicy.get("status").asText());
        assertEquals(0, call("GET", "/api/v1/customers/me/protection/lift-policies", otherCustomer, null, 200).get("items").size());
        call("GET", "/api/v1/protection/lift-policies/" + liftPolicyId, otherCustomer, null, 403);
        call("POST", "/api/v1/customers/me/protection/lift-policies", otherCustomer,
                Map.of("liftId", liftId, "providerId", providerId, "insurerPolicyNumber", "CROSS-OWNER",
                        "coverageStartDate", LocalDate.now().toString(), "coverageEndDate", LocalDate.now().plusDays(1).toString()), 403);
        call("GET", "/api/v1/customers/me/protection/lift-policies", techOwner, null, 403);

        call("PATCH", "/api/v1/admin/protection/lift-policies/" + liftPolicyId + "/insurer-confirmation", admin,
                Map.of("status", "INSURER_CONFIRMED_ACTIVE", "insurerReference", "INSURER-REF-100",
                        "insurerReviewNote", "Status confirmed from insurer correspondence"), 200);
        call("POST", "/api/v1/protection/lift-policies/" + liftPolicyId + "/documents", owner,
                Map.of("documentType", "POLICY_SCHEDULE", "originalFilename", "policy.pdf", "contentType", "application/pdf",
                        "fileSize", 3200, "storageKey", "protection/" + UUID.randomUUID() + "/policy.pdf"), 200);
        protection.generateExpiryNotices();
        assertEquals(1, call("GET", "/api/v1/protection/expiry-notices", owner, null, 200).get("items").size());
        JsonNode application = call("POST", "/api/v1/customers/me/protection/applications", owner,
                Map.of("liftId", liftId, "providerId", providerId, "productId", liftProductId,
                        "coverageRequested", "Request a quote from the insurer"), 200);
        assertEquals("SUBMITTED", application.get("status").asText());
        JsonNode quoted = call("POST", "/api/v1/admin/protection/applications/" + application.get("id").asLong() + "/manual-quote", admin,
                Map.of("quoteAmount", 12500, "quoteCurrency", "INR", "quoteExpiresOn", LocalDate.now().plusDays(14).toString(),
                        "insurerQuoteReference", "EXTERNAL-QUOTE-1", "insurerNotes", "Manual quote communicated by insurer"), 200);
        assertEquals("QUOTE_RECEIVED", quoted.get("status").asText());
        JsonNode decision = call("POST", "/api/v1/customers/me/protection/applications/" + application.get("id").asLong() + "/decision", owner,
                Map.of("status", "CUSTOMER_ACCEPTED"), 200);
        assertEquals("CUSTOMER_ACCEPTED", decision.get("status").asText());

        JsonNode claim = call("POST", "/api/v1/protection/claims", owner,
                Map.of("policyType", "LIFT", "policyId", liftPolicyId, "incidentDate", LocalDate.now().toString(),
                        "description", "Damage reported for insurer assessment", "claimedAmount", 2500, "currency", "INR"), 200);
        long claimId = claim.get("id").asLong();
        call("POST", "/api/v1/protection/claims/" + claimId + "/documents", owner,
                Map.of("documentType", "INCIDENT_PHOTO", "originalFilename", "lift-damage.jpg", "contentType", "image/jpeg",
                        "fileSize", 3200, "storageKey", "protection/" + UUID.randomUUID() + "/photo.jpg"), 200);
        call("PATCH", "/api/v1/admin/protection/claims/" + claimId + "/insurer-status", admin,
                Map.of("status", "FORWARDED_TO_INSURER"), 200);
        call("PATCH", "/api/v1/admin/protection/claims/" + claimId + "/insurer-status", admin,
                Map.of("status", "INSURER_DECISION_PENDING"), 200);
        JsonNode claimDecision = call("PATCH", "/api/v1/admin/protection/claims/" + claimId + "/insurer-status", admin,
                Map.of("status", "INSURER_APPROVED", "insurerClaimReference", "INSURER-CLAIM-1",
                        "insurerResponseNote", "Approval outcome reported by insurer"), 200);
        assertEquals("INSURER_APPROVED", claimDecision.get("status").asText());
        call("PATCH", "/api/v1/admin/protection/claims/" + claimId + "/insurer-status", admin,
                Map.of("status", "SETTLED"), 400);
        call("GET", "/api/v1/protection/claims/" + claimId, otherCustomer, null, 403);
        assertEquals(1, call("GET", "/api/v1/protection/claims/" + claimId + "/documents", owner, null, 200).size());

        JsonNode technicianPolicy = call("POST", "/api/v1/technicians/me/protection/policies", techOwner,
                Map.of("providerId", providerId, "productId", technicianProduct.get("id").asLong(),
                        "insurerPolicyNumber", "EXISTING-TECH-POLICY-" + UUID.randomUUID(),
                        "coverageStartDate", LocalDate.now().minusDays(1).toString(),
                        "coverageEndDate", LocalDate.now().plusDays(15).toString(), "currency", "INR"), 200);
        call("GET", "/api/v1/protection/technician-policies/" + technicianPolicy.get("id").asLong(), otherTechnician, null, 403);
        assertEquals(0, call("GET", "/api/v1/technicians/me/protection/policies", otherTechnician, null, 200).get("items").size());
        call("PATCH", "/api/v1/admin/protection/technician-policies/" + technicianPolicy.get("id").asLong() + "/insurer-confirmation", admin,
                Map.of("status", "INSURER_CONFIRMED_ACTIVE", "insurerReference", "INSURER-TECH-REF",
                        "insurerReviewNote", "Status confirmed from insurer correspondence"), 200);
        protection.generateExpiryNotices();
        JsonNode technicianNotices = call("GET", "/api/v1/protection/expiry-notices", techOwner, null, 200);
        assertEquals(1, technicianNotices.get("items").size());
        assertEquals(15, technicianNotices.get("items").get(0).get("daysBeforeExpiry").asInt());

        JsonNode renewal = call("POST", "/api/v1/protection/renewals", owner,
                Map.of("policyType", "LIFT", "policyId", liftPolicyId, "note", "Please request renewal information"), 200);
        assertEquals("REQUESTED", renewal.get("status").asText());
        JsonNode renewed = call("PATCH", "/api/v1/admin/protection/renewals/" + renewal.get("id").asLong() + "/insurer-status", admin,
                Map.of("status", "INSURER_CONFIRMED", "insurerReference", "RENEWAL-REFERENCE",
                        "insurerResponseNote", "Renewal confirmed by insurer; policy document to follow"), 200);
        assertEquals("INSURER_CONFIRMED", renewed.get("status").asText());
        assertEquals(1, call("GET", "/api/v1/protection/dashboard", owner, null, 200).get("liftPolicies").asInt());
        call("GET", "/api/v1/admin/protection/claims?size=101", admin, null, 400);
    }

    private User customer(String name) {
        User user = user(Role.CUSTOMER);
        CustomerProfile profile = new CustomerProfile();
        profile.setUser(user);
        profile.setFullName(name);
        customers.saveAndFlush(profile);
        return user;
    }

    private User technician() {
        User user = user(Role.TECHNICIAN);
        TechnicianProfile profile = new TechnicianProfile();
        profile.user = user;
        technicians.saveAndFlush(profile);
        return user;
    }

    private User user(Role role) {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@protection.test");
        user.setRole(role);
        return users.saveAndFlush(user);
    }

    private JsonNode call(String method, String path, User actor, Object body, int expected) throws Exception {
        var request = switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "PATCH" -> patch(path);
            default -> throw new IllegalArgumentException(method);
        };
        request.header("Authorization", "Bearer " + jwt.issue(actor));
        if (body != null) request.contentType("application/json").content(json.writeValueAsString(body));
        var result = mvc.perform(request).andReturn().getResponse();
        String response = result.getContentAsString();
        assertEquals(expected, result.getStatus(), method + " " + path + " returned " + response);
        JsonNode envelope = json.readTree(response);
        assertEquals(expected < 400, envelope.get("success").asBoolean());
        assertEquals(expected, envelope.get("status").asInt());
        return envelope.get("data");
    }
}
