package com.valor.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.env.MockEnvironment;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TechnicianApplicationSecurityTest {
    @Autowired TechnicianApplicationService service;
    @Autowired TechnicianApplicationRepository applications;

    private TechnicianApplicationService.Created draft() {
        return service.create(new TechnicianApplicationController.Create("Security Test", UUID.randomUUID()+"@example.test", "+919876543210", "Test-only-password-123"));
    }

    @Test void productionAndDefaultProfilesRejectDevelopmentOtp() {
        for (String[] profiles : new String[][] {{"prod"}, {"dev", "prod"}, {}}) {
            var env = new MockEnvironment(); env.setActiveProfiles(profiles);
            var guarded = new TechnicianApplicationService(null, null, null, null, null, null, null, env, null);
            assertThrows(IllegalArgumentException.class, () -> guarded.sendOtp(1L, "token"));
            assertThrows(IllegalArgumentException.class, () -> guarded.verifyOtp(1L, "token", "1111"));
        }
    }

    @Test void failedAttemptsCommitAndResendCannotResetLockout() {
        var created = draft(); var id = created.application().id(); var token = created.applicationToken();
        var sent = service.sendOtp(id, token);
        String wrong = sent.otp().equals("0000") ? "0001" : "0000";
        for (int remaining = 2; remaining >= 0; remaining--) {
            assertThrows(IllegalArgumentException.class, () -> service.verifyOtp(id, token, wrong));
            assertEquals(remaining, applications.findById(id).orElseThrow().otpAttemptsRemaining);
        }
        assertThrows(IllegalArgumentException.class, () -> service.verifyOtp(id, token, sent.otp()));
        assertThrows(IllegalArgumentException.class, () -> service.sendOtp(id, token));
    }

    @Test void verifiedOtpCannotBeReplayedOrRegenerated() {
        var created = draft(); var id = created.application().id(); var token = created.applicationToken();
        var sent = service.sendOtp(id, token);
        assertThrows(IllegalArgumentException.class, () -> service.sendOtp(id, token));
        assertEquals("OTP_VERIFIED", service.verifyOtp(id, token, sent.otp()).status());
        assertThrows(IllegalArgumentException.class, () -> service.verifyOtp(id, token, sent.otp()));
        assertThrows(IllegalArgumentException.class, () -> service.sendOtp(id, token));
    }

    @Test void submittedApplicationCannotBeEditedResetOrResubmitted() {
        var created = draft(); var id = created.application().id(); var token = created.applicationToken();
        var sent = service.sendOtp(id, token); service.verifyOtp(id, token, sent.otp());
        var row = applications.findById(id).orElseThrow(); row.status = "SUBMITTED"; applications.saveAndFlush(row);
        assertThrows(IllegalArgumentException.class, () -> service.update(id, token, null));
        assertThrows(IllegalArgumentException.class, () -> service.uploadDocument(id, token, "PROFILE_PHOTO", null));
        assertThrows(IllegalArgumentException.class, () -> service.sendOtp(id, token));
        assertThrows(IllegalArgumentException.class, () -> service.submit(id, token));
        assertEquals("SUBMITTED", applications.findById(id).orElseThrow().status);
    }

    @Test void applicationTokenIsRequiredForPrivateData() {
        var created = draft();
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.read(created.application().id(), "wrong-token"));
    }

    @Test void documentCorrectionsRemainAvailableUntilVerification() {
        var created = draft(); var id = created.application().id(); var token = created.applicationToken();
        var sent = service.sendOtp(id, token); service.verifyOtp(id, token, sent.otp());
        var file = new org.springframework.mock.web.MockMultipartFile("file", "document.pdf", "application/pdf", "%PDF-1.4 test".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        for (String state : new String[] {"DOCUMENTS_PENDING", "DOCUMENTS_REJECTED"}) {
            var row = applications.findById(id).orElseThrow(); row.status = state; applications.saveAndFlush(row);
            assertEquals("PENDING", service.uploadDocument(id, token, "PROFILE_PHOTO", file).reviewStatus());
            assertEquals("DOCUMENTS_PENDING", applications.findById(id).orElseThrow().status);
        }
        var row = applications.findById(id).orElseThrow(); row.status = "DOCUMENTS_VERIFIED"; applications.saveAndFlush(row);
        assertThrows(IllegalArgumentException.class, () -> service.uploadDocument(id, token, "PROFILE_PHOTO", file));
    }

    @Test void documentCannotImpersonateAnAllowedType() {
        var created = draft();
        var fake = new org.springframework.mock.web.MockMultipartFile("file", "photo.png", "image/png", "<script>alert(1)</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> service.uploadDocument(created.application().id(), created.applicationToken(), "PROFILE_PHOTO", fake));
    }
}
