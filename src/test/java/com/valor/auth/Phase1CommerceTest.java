package com.valor.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.valor.workflow.*;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:phase1_commerce;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.asset-documents.local-root=target/test-asset-documents",
        "razorpay.key-id=rzp_test_key", "razorpay.key-secret=rzp_test_secret",
        "razorpay.webhook-secret=test_webhook_secret", "razorpay.offline-test-mode=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase1CommerceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepo users;
    @Autowired CustomerRepo customers;
    @Autowired TechRepo technicians;
    @Autowired JwtService jwt;
    @Autowired EntityManager em;

    record Fixture(User admin, User customer, User technician, Long customerId, Long technicianId,
            long buildingId, long liftId, long amcId, long requestId) {}

    private User user(Role role) {
        User user = new User(); user.setEmail(UUID.randomUUID() + "@example.test"); user.setRole(role);
        return users.saveAndFlush(user);
    }
    private CustomerProfile customer() {
        CustomerProfile p = new CustomerProfile(); p.setUser(user(Role.CUSTOMER)); p.setFullName("Commerce customer");
        return customers.saveAndFlush(p);
    }
    private TechnicianProfile technician() {
        TechnicianProfile profile = new TechnicianProfile(); profile.user = user(Role.TECHNICIAN);
        return technicians.saveAndFlush(profile);
    }
    private JsonNode call(MockHttpServletRequestBuilder request, User actor, Object body, int expected) throws Exception {
        if (actor != null) request.header("Authorization", "Bearer " + jwt.issue(actor));
        if (body != null) request.contentType("application/json").content(json.writeValueAsString(body));
        String text = mvc.perform(request).andExpect(status().is(expected)).andExpect(jsonPath("$.status").value(expected))
                .andReturn().getResponse().getContentAsString();
        for (String secret : List.of("passwordHash", "tokenHash", "storageKey", "stackTrace", "com.valor", "org.hibernate")) {
            assertFalse(text.contains(secret), secret);
        }
        return json.readTree(text).get("data");
    }
    private String signed(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test_webhook_secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append("%02x".formatted(b));
        return out.toString();
    }
    private JsonNode webhook(String payload, int expected) throws Exception {
        String text = mvc.perform(post("/api/v1/webhooks/razorpay").contentType("application/json").content(payload)
                .header("X-Razorpay-Signature", signed(payload))).andExpect(status().is(expected))
                .andExpect(jsonPath("$.status").value(expected)).andReturn().getResponse().getContentAsString();
        return json.readTree(text).get("data");
    }
    private Fixture fixture() throws Exception {
        User admin = user(Role.ADMIN);
        CustomerProfile owner = customer();
        TechnicianProfile tech = technician();
        long building = call(post("/api/v1/buildings"), admin,
                Map.of("customerProfileId", owner.getId(), "buildingName", "Phase 1 Tower"), 200).get("id").asLong();
        long lift = call(post("/api/v1/lifts"), admin, Map.of("buildingId", building, "name", "Phase 1 Lift"), 200).get("id").asLong();
        long amc = call(post("/api/v1/amc-contracts"), admin, Map.of("liftId", lift, "amcNumber", UUID.randomUUID().toString(),
                "plan", "Standard", "startDate", "2030-01-01", "endDate", "2030-12-31"), 200).get("id").asLong();
        long request = call(post("/api/v1/service-requests"), owner.getUser(), Map.of("liftId", lift, "title", "Need service",
                "description", "Phase 1 issue", "serviceType", "BREAKDOWN"), 200).at("/request/id").asLong();
        call(post("/api/v1/service-requests/" + request + "/assignments"), admin, Map.of("technicianProfileId", tech.getId()), 200);
        return new Fixture(admin, owner.getUser(), tech.getUser(), owner.getId(), tech.getId(), building, lift, amc, request);
    }
    private void startTracking(Fixture f) throws Exception {
        long assignment = call(post("/api/v1/service-requests/" + f.requestId() + "/assignments"), f.admin(),
                Map.of("technicianProfileId", f.technicianId()), 200).at("/activeAssignment/id").asLong();
        call(post("/api/v1/service-requests/" + f.requestId() + "/assignments/" + assignment + "/accept"), f.technician(), null, 200);
        call(post("/api/v1/service-requests/" + f.requestId() + "/status"), f.technician(), Map.of("toStatus", "ON_THE_WAY"), 200);
    }

    @Test void paymentsAndInvoicesAreCanonicalScopedAndLifecycleControlled() throws Exception {
        Fixture f = fixture();
        User other = customer().getUser();
        long invoice = call(post("/api/v1/invoices"), f.admin(), Map.of("customerProfileId", f.customerId(),
                "serviceRequestId", f.requestId(), "description", "Service charge", "subtotal", 1000, "taxAmount", 180, "currency", "INR"), 200).get("id").asLong();
        call(post("/api/v1/payments"), f.customer(), Map.of("invoiceId", invoice, "amount", 10,
                "currency", "INR", "purpose", "INVOICE"), 400);
        JsonNode payment = call(post("/api/v1/payments"), f.customer(), Map.of("invoiceId", invoice, "amount", 1180,
                "currency", "INR", "purpose", "INVOICE"), 200);
        assertEquals("PENDING", payment.get("status").asText());
        call(put("/api/v1/payments/" + payment.get("id").asLong() + "/status"), f.admin(),
                Map.of("status", "SUCCEEDED", "providerReference", "LOCAL-TEST"), 200);
        call(get("/api/v1/payments/" + payment.get("id").asLong()), f.customer(), null, 200);
        call(get("/api/v1/payments/" + payment.get("id").asLong()), other, null, 403);
        call(get("/api/v1/invoices/" + invoice), f.customer(), null, 200);
        call(put("/api/v1/invoices/" + invoice + "/status"), other, Map.of("status", "PAID"), 403);
    }

    @Test void razorpayCheckoutWebhookAndRefundLifecycleAreIdempotentAndTrusted() throws Exception {
        Fixture f = fixture();
        User other = customer().getUser();
        long invoice = call(post("/api/v1/invoices"), f.admin(), Map.of("customerProfileId", f.customerId(),
                "serviceRequestId", f.requestId(), "description", "Gateway invoice", "subtotal", 1000, "taxAmount", 180, "currency", "INR"), 200).get("id").asLong();
        call(post("/api/v1/payments/razorpay/checkout"), other, Map.of("invoiceId", invoice), 403);
        JsonNode checkout = call(post("/api/v1/payments/razorpay/checkout"), f.customer(), Map.of("invoiceId", invoice), 200);
        assertEquals(1180, checkout.get("amount").asInt());
        assertEquals("INR", checkout.get("currency").asText());
        long paymentId = checkout.get("paymentId").asLong();
        String orderId = checkout.get("razorpayOrderId").asText();
        call(post("/api/v1/payments/razorpay/checkout"), f.customer(), Map.of("invoiceId", invoice), 409);

        String captured = """
                {"id":"evt_capture_1","event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_test_1","order_id":"%s","status":"captured"}}}}
                """.formatted(orderId);
        mvc.perform(post("/api/v1/webhooks/razorpay").contentType("application/json").content(captured)
                .header("X-Razorpay-Signature", "bad")).andExpect(status().isBadRequest());
        webhook(captured, 200);
        webhook(captured, 200);
        JsonNode paid = call(get("/api/v1/payments/" + paymentId), f.customer(), null, 200);
        assertEquals("SUCCEEDED", paid.get("status").asText());
        assertEquals("pay_test_1", paid.get("razorpayPaymentId").asText());
        assertEquals("PAID", call(get("/api/v1/invoices/" + invoice), f.customer(), null, 200).get("status").asText());
        call(post("/api/v1/payments/razorpay/checkout"), f.customer(), Map.of("invoiceId", invoice), 409);

        String unknown = """
                {"id":"evt_unknown_1","event":"payment.we_dont_handle","payload":{"payment":{"entity":{"id":"pay_test_1","order_id":"%s","status":"mystery"}}}}
                """.formatted(orderId);
        webhook(unknown, 200);

        call(post("/api/v1/payments/" + paymentId + "/refunds"), f.customer(), Map.of("amount", 100), 403);
        call(post("/api/v1/payments/" + paymentId + "/refunds"), f.admin(), Map.of("amount", 2000), 409);
        call(post("/api/v1/payments/" + paymentId + "/refunds"), f.admin(), Map.of("amount", 500, "reason", "partial"), 200);
        call(post("/api/v1/payments/" + paymentId + "/refunds"), f.admin(), Map.of("amount", 100, "reason", "duplicate"), 409);
        String refundId = call(get("/api/v1/payments/" + paymentId + "/refunds"), f.admin(), null, 200).get(0).get("razorpayRefundId").asText();
        String refundWebhook = """
                {"id":"evt_refund_1","event":"refund.processed","payload":{"refund":{"entity":{"id":"%s","status":"processed"}}}}
                """.formatted(refundId);
        webhook(refundWebhook, 200);
        assertEquals("PARTIALLY_REFUNDED", call(get("/api/v1/payments/" + paymentId), f.admin(), null, 200).get("status").asText());
        call(post("/api/v1/payments/" + paymentId + "/refunds"), f.admin(), Map.of("amount", 680, "reason", "rest"), 200);
        String finalRefundId = call(get("/api/v1/payments/" + paymentId + "/refunds"), f.admin(), null, 200).get(1).get("razorpayRefundId").asText();
        String finalRefundWebhook = """
                {"id":"evt_refund_2","event":"refund.processed","payload":{"refund":{"entity":{"id":"%s","status":"processed"}}}}
                """.formatted(finalRefundId);
        webhook(finalRefundWebhook, 200);
        assertEquals("REFUNDED", call(get("/api/v1/payments/" + paymentId), f.admin(), null, 200).get("status").asText());
    }

    @Test void technicianLatestLocationFollowsAssignmentOwnershipAndLifecycle() throws Exception {
        Fixture f = fixture(), other = fixture();
        call(post("/api/v1/technician/me/jobs/" + f.requestId() + "/location"), f.technician(),
                Map.of("latitude", 12.9716, "longitude", 77.5946, "timestamp", "2026-09-16T10:00:00"), 409);
        startTracking(f);
        call(get("/api/v1/customers/me/service-requests/" + f.requestId() + "/technician-location"), f.customer(), null, 404);
        call(post("/api/v1/technician/me/jobs/" + f.requestId() + "/location"), other.technician(),
                Map.of("latitude", 12.9716, "longitude", 77.5946, "timestamp", "2026-09-16T10:00:00"), 403);
        call(post("/api/v1/technician/me/jobs/" + f.requestId() + "/location"), f.technician(),
                Map.of("latitude", 91, "longitude", 77.5946, "timestamp", "2026-09-16T10:00:00"), 400);
        call(post("/api/v1/technician/me/jobs/" + f.requestId() + "/location"), f.technician(),
                Map.of("latitude", 12.9716, "longitude", 181, "timestamp", "2026-09-16T10:00:00"), 400);
        call(post("/api/v1/technician/me/jobs/" + f.requestId() + "/location"), f.technician(),
                Map.of("latitude", 12.9716, "longitude", 77.5946, "timestamp", "2026-09-16T10:00:00"), 200);
        JsonNode seen = call(get("/api/v1/customers/me/service-requests/" + f.requestId() + "/technician-location"), f.customer(), null, 200);
        assertEquals("2026-09-16T10:00:00", seen.get("timestamp").asText());
        call(get("/api/v1/customers/me/service-requests/" + f.requestId() + "/technician-location"), other.customer(), null, 403);
        call(post("/api/v1/service-requests/" + f.requestId() + "/status"), f.technician(), Map.of("toStatus", "REACHED_SITE"), 200);
        call(post("/api/v1/service-requests/" + f.requestId() + "/status"), f.technician(), Map.of("toStatus", "DIAGNOSIS"), 200);
        call(post("/api/v1/service-requests/" + f.requestId() + "/status"), f.technician(), Map.of("toStatus", "REPAIR_IN_PROGRESS"), 200);
        call(post("/api/v1/service-requests/" + f.requestId() + "/status"), f.technician(), Map.of("toStatus", "TESTING"), 200);
        call(post("/api/v1/technician/me/jobs/" + f.requestId() + "/report"), f.technician(),
                Map.of("diagnosis", "Diagnosed", "workPerformed", "Repaired", "testingResult", "Passed"), 200);
        call(post("/api/v1/service-requests/" + f.requestId() + "/status"), f.technician(), Map.of("toStatus", "COMPLETED"), 200);
        call(post("/api/v1/technician/me/jobs/" + f.requestId() + "/location"), f.technician(),
                Map.of("latitude", 12.9716, "longitude", 77.5946), 403);
        call(get("/api/v1/customers/me/service-requests/" + f.requestId() + "/technician-location"), f.customer(), null, 409);
    }

    @Test void supportTicketsCanBeCreatedByCustomersAndTechniciansButManagedByAdmin() throws Exception {
        Fixture f = fixture();
        long ticket = call(post("/api/v1/support-tickets"), f.customer(), Map.of("serviceRequestId", f.requestId(),
                "category", "SERVICE_REQUEST", "priority", "HIGH", "subject", "Need help", "description", "Please review"), 200).get("id").asLong();
        call(get("/api/v1/support-tickets/" + ticket), f.customer(), null, 200);
        call(put("/api/v1/support-tickets/" + ticket + "/status"), f.customer(), Map.of("status", "RESOLVED"), 403);
        JsonNode updated = call(put("/api/v1/support-tickets/" + ticket + "/status"), f.admin(),
                Map.of("status", "IN_PROGRESS", "adminNotes", "Assigned"), 200);
        assertEquals("IN_PROGRESS", updated.get("status").asText());
        call(post("/api/v1/support-tickets"), f.technician(), Map.of("serviceRequestId", f.requestId(),
                "subject", "Technician issue", "description", "Tool issue"), 200);
    }

    @Test void customerAmcRenewalRequiresQuoteAndSuccessfulPaymentBeforeApplyingRenewal() throws Exception {
        Fixture f = fixture();
        long renewal = call(post("/api/v1/customers/me/amc-contracts/" + f.amcId() + "/renewal-requests"), f.customer(),
                Map.of("requestedStartDate", "2031-01-01", "requestedEndDate", "2031-12-31", "customerNotes", "Renew please"), 200).get("id").asLong();
        call(post("/api/v1/customers/me/amc-contracts/" + f.amcId() + "/renewal-requests"), f.customer(),
                Map.of("requestedStartDate", "2031-01-01", "requestedEndDate", "2031-12-31"), 409);
        long invoice = call(post("/api/v1/invoices"), f.admin(), Map.of("customerProfileId", f.customerId(), "amcContractId", f.amcId(),
                "description", "AMC renewal", "subtotal", 5000, "taxAmount", 900), 200).get("id").asLong();
        long payment = call(post("/api/v1/payments"), f.customer(), Map.of("invoiceId", invoice, "amount", 5900, "purpose", "AMC_RENEWAL"), 200).get("id").asLong();
        call(put("/api/v1/amc-renewal-requests/" + renewal + "/quote"), f.admin(), Map.of("quotedAmount", 5900,
                "currency", "INR", "invoiceId", invoice, "paymentId", payment), 200);
        call(put("/api/v1/amc-renewal-requests/" + renewal + "/status"), f.admin(), Map.of("status", "RENEWED", "paymentId", payment), 409);
        call(put("/api/v1/payments/" + payment + "/status"), f.admin(), Map.of("status", "SUCCEEDED"), 200);
        JsonNode applied = call(put("/api/v1/amc-renewal-requests/" + renewal + "/status"), f.admin(),
                Map.of("status", "RENEWED", "paymentId", payment, "invoiceId", invoice), 200);
        assertEquals("RENEWED", applied.get("status").asText());
        assertEquals(LocalDate.parse("2031-12-31"), em.createQuery("select a.endDate from AmcContract a where a.id=:id", LocalDate.class)
                .setParameter("id", f.amcId()).getSingleResult());
    }

    @Test void buildingAndLiftDocumentsUseSafeOwnershipAndStorageMetadata() throws Exception {
        Fixture f = fixture(), other = fixture();
        MockMultipartFile pdf = new MockMultipartFile("file", "manual.pdf", "application/pdf", "%PDF test".getBytes());
        long doc = json.readTree(mvc.perform(multipart("/api/v1/customers/me/buildings/" + f.buildingId() + "/documents").file(pdf)
                .header("Authorization", "Bearer " + jwt.issue(f.customer())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.storageKey").doesNotExist())
                .andReturn().getResponse().getContentAsString()).at("/data/id").asLong();
        mvc.perform(get("/api/v1/customers/me/buildings/" + f.buildingId() + "/documents")
                .header("Authorization", "Bearer " + jwt.issue(f.customer())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(doc));
        mvc.perform(get("/api/v1/customers/me/buildings/" + f.buildingId() + "/documents/" + doc)
                .header("Authorization", "Bearer " + jwt.issue(f.customer())))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/pdf"));
        call(get("/api/v1/customers/me/buildings/" + f.buildingId() + "/documents"), other.customer(), null, 404);
        MockMultipartFile exe = new MockMultipartFile("file", "bad.exe", "application/x-msdownload", new byte[]{0x4D, 0x5A, 0});
        mvc.perform(multipart("/api/v1/customers/me/lifts/" + f.liftId() + "/documents").file(exe)
                .header("Authorization", "Bearer " + jwt.issue(f.customer()))).andExpect(status().isBadRequest());
        call(delete("/api/v1/customers/me/buildings/" + f.buildingId() + "/documents/" + doc), f.customer(), null, 200);
        mvc.perform(get("/api/v1/customers/me/buildings/" + f.buildingId() + "/documents")
                .header("Authorization", "Bearer " + jwt.issue(f.customer())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        call(get("/api/v1/customers/me/buildings/" + f.buildingId() + "/documents/" + doc), f.customer(), null, 404);
        call(get("/api/v1/customers/me/buildings/999999/documents"), f.customer(), null, 404);
    }

    @Test void adminVisitChangeRequestListPagesAndDecisionsRemainCompatible() throws Exception {
        Fixture f = fixture();
        long assignment = call(post("/api/v1/service-requests/" + f.requestId() + "/assignments"), f.admin(),
                Map.of("technicianProfileId", f.technicianId()), 200).at("/activeAssignment/id").asLong();
        call(post("/api/v1/service-requests/" + f.requestId() + "/assignments/" + assignment + "/accept"), f.technician(), null, 200);
        long visit = call(post("/api/v1/admin/service-visits"), f.admin(), Map.of("serviceRequestId", f.requestId(),
                "technicianProfileId", f.technicianId(), "scheduledDate", "2030-02-01", "startTime", "10:00:00",
                "endTime", "11:00:00", "notes", "Initial"), 200).get("id").asLong();
        long pending = call(post("/api/v1/technician/me/visits/" + visit + "/reschedule-requests"), f.technician(),
                Map.of("reason", "Need a later slot", "requestedDate", "2030-02-02", "requestedStartTime", "12:00:00",
                        "requestedEndTime", "13:00:00"), 200).get("id").asLong();
        JsonNode page = call(get("/api/v1/admin/visit-change-requests?status=PENDING&page=0&size=1"), f.admin(), null, 200);
        assertEquals(1, page.get("items").size());
        assertTrue(page.get("totalElements").asLong() >= 1);
        call(get("/api/v1/admin/visit-change-requests?status=PENDING&page=99&size=1"), f.admin(), null, 200);
        call(get("/api/v1/admin/visit-change-requests?status=APPROVED&page=0&size=20"), f.admin(), null, 200);
        call(get("/api/v1/admin/visit-change-requests?status=PENDING&page=0&size=20"), f.customer(), null, 403);
        call(get("/api/v1/admin/visit-change-requests?status=PENDING&page=-1&size=20"), f.admin(), null, 400);
        call(post("/api/v1/admin/visit-change-requests/" + pending + "/approve"), f.admin(), Map.of("reviewNotes", "Approved"), 200);

        Fixture rejectFixture = fixture();
        long rejectAssignment = call(post("/api/v1/service-requests/" + rejectFixture.requestId() + "/assignments"), rejectFixture.admin(),
                Map.of("technicianProfileId", rejectFixture.technicianId()), 200).at("/activeAssignment/id").asLong();
        call(post("/api/v1/service-requests/" + rejectFixture.requestId() + "/assignments/" + rejectAssignment + "/accept"), rejectFixture.technician(), null, 200);
        long rejectVisit = call(post("/api/v1/admin/service-visits"), rejectFixture.admin(), Map.of("serviceRequestId", rejectFixture.requestId(),
                "technicianProfileId", rejectFixture.technicianId(), "scheduledDate", "2030-03-01", "startTime", "10:00:00",
                "endTime", "11:00:00"), 200).get("id").asLong();
        long rejected = call(post("/api/v1/technician/me/visits/" + rejectVisit + "/reschedule-requests"), rejectFixture.technician(),
                Map.of("reason", "Need a later slot", "requestedDate", "2030-03-02", "requestedStartTime", "12:00:00",
                        "requestedEndTime", "13:00:00"), 200).get("id").asLong();
        assertEquals("REJECTED", call(post("/api/v1/admin/visit-change-requests/" + rejected + "/reject"),
                rejectFixture.admin(), Map.of("reason", "No capacity"), 200).get("status").asText());
    }

    @Test void feedbackReadFollowsCustomerOnlyContract() throws Exception {
        Fixture f = fixture(), other = fixture();
        call(post("/api/v1/service-requests/" + f.requestId() + "/assignments"), f.admin(), Map.of("technicianProfileId", f.technicianId()), 200);
        call(post("/api/v1/service-requests/" + f.requestId() + "/assignments/" + call(get("/api/v1/service-requests/" + f.requestId()), f.admin(), null, 200).at("/activeAssignment/id").asLong() + "/accept"), f.technician(), null, 200);
        call(post("/api/v1/service-requests/" + f.requestId() + "/status"), f.technician(), Map.of("toStatus", "ON_THE_WAY"), 200);
        call(post("/api/v1/service-requests/" + f.requestId() + "/status"), f.technician(), Map.of("toStatus", "REACHED_SITE"), 200);
        call(post("/api/v1/service-requests/" + f.requestId() + "/status"), f.technician(), Map.of("toStatus", "DIAGNOSIS"), 200);
        call(post("/api/v1/service-requests/" + f.requestId() + "/status"), f.technician(), Map.of("toStatus", "REPAIR_IN_PROGRESS"), 200);
        call(post("/api/v1/service-requests/" + f.requestId() + "/status"), f.technician(), Map.of("toStatus", "TESTING"), 200);
        call(post("/api/v1/technician/me/jobs/" + f.requestId() + "/report"), f.technician(),
                Map.of("diagnosis", "Diagnosed", "workPerformed", "Repaired", "testingResult", "Passed"), 200);
        call(post("/api/v1/service-requests/" + f.requestId() + "/status"), f.technician(), Map.of("toStatus", "COMPLETED"), 200);
        JsonNode feedback = call(put("/api/v1/service-requests/" + f.requestId() + "/feedback"), f.customer(),
                Map.of("rating", 5, "comment", "Good work"), 200);
        assertEquals(5, feedback.get("rating").asInt());
        assertEquals("Good work", call(get("/api/v1/service-requests/" + f.requestId() + "/feedback"), f.customer(), null, 200).get("comment").asText());
        call(get("/api/v1/service-requests/" + f.requestId() + "/feedback"), other.customer(), null, 403);
        call(get("/api/v1/service-requests/" + f.requestId() + "/feedback"), f.admin(), null, 403);
        mvc.perform(get("/api/v1/service-requests/" + f.requestId() + "/feedback")).andExpect(status().isUnauthorized());
    }

    @Test void serviceReportPdfDownloadsForAuthorizedAssignedActors() throws Exception {
        Fixture f = fixture();
        call(post("/api/v1/service-requests/" + f.requestId() + "/assignments"), f.admin(), Map.of("technicianProfileId", f.technicianId()), 200);
        call(post("/api/v1/technician/me/jobs/" + f.requestId() + "/report"), f.technician(),
                Map.of("diagnosis", "Diagnosed", "workPerformed", "Repaired", "testingResult", "Passed"), 200);
        mvc.perform(get("/api/v1/service-requests/" + f.requestId() + "/report.pdf")
                .header("Authorization", "Bearer " + jwt.issue(f.technician())))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/pdf"));
        mvc.perform(get("/api/v1/service-requests/" + f.requestId() + "/report.pdf")
                .header("Authorization", "Bearer " + jwt.issue(customer().getUser())))
                .andExpect(status().isForbidden());
    }
}
