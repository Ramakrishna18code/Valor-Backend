package com.valor.workflow;

import com.valor.auth.*;
import java.security.SecureRandom;
import java.time.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class CompletionOtpService {
    private final CompletionOtpRepository otps; private final RequestRepository requests; private final AssignmentRepository assignments;
    private final AssetIdentityAccess identities; private final WorkflowIdentityAccess profiles; private final PasswordEncoder encoder;
    private final CompletionOtpSender sender; private final Clock clock; private final SecureRandom random = new SecureRandom();
    private final boolean enabled;
    CompletionOtpService(CompletionOtpRepository otps, RequestRepository requests, AssignmentRepository assignments,
            AssetIdentityAccess identities, WorkflowIdentityAccess profiles, PasswordEncoder encoder, CompletionOtpSender sender, Clock clock,
            @Value("${valor.completion-otp.enabled:false}") boolean enabled) {
        this.otps = otps; this.requests = requests; this.assignments = assignments; this.identities = identities; this.profiles = profiles;
        this.encoder = encoder; this.sender = sender; this.clock = clock; this.enabled = enabled;
    }
    CompletionOtpView request(Long requestId) {
        TechnicianProfile technician = profiles.technician(identities.actor());
        ServiceRequest request = assigned(requestId, technician);
        CompletionOtp otp = new CompletionOtp(); otp.request = request; otp.customer = request.getCustomer(); otp.technician = technician;
        String code = String.valueOf(100000 + random.nextInt(900000));
        otp.otpHash = encoder.encode(code); otp.expiresAt = LocalDateTime.now(clock).plusMinutes(10);
        otps.saveAndFlush(otp); sender.sendCompletionOtp(request, code);
        return state(otp, false);
    }
    CompletionOtpView verify(Long requestId, Long otpId, String code) {
        TechnicianProfile technician = profiles.technician(identities.actor());
        assigned(requestId, technician);
        CompletionOtp otp = otps.lockById(otpId).orElseThrow(CompletionOtpService::invalid);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!otp.request.getId().equals(requestId) || !otp.technician.getId().equals(technician.getId()) || otp.verifiedAt != null
                || !otp.expiresAt.isAfter(now) || otp.attemptsRemaining <= 0 || otp.lockedUntil != null && otp.lockedUntil.isAfter(now)) throw invalid();
        if (code == null || !encoder.matches(code, otp.otpHash)) {
            otp.attemptsRemaining--;
            if (otp.attemptsRemaining <= 0) otp.lockedUntil = now.plusMinutes(15);
            throw invalid();
        }
        otp.verifiedAt = now;
        return state(otp, true);
    }
    void requireVerified(Long requestId) {
        if (!enabled) return;
        if (otps.findTopByRequestIdAndVerifiedAtIsNotNullOrderByVerifiedAtDesc(requestId).isEmpty()) {
            throw new WorkflowException(409, "Completion OTP verification required");
        }
    }
    CompletionOtpView latest(Long requestId) {
        User actor = identities.actor();
        ServiceRequest request = requests.findById(requestId).orElseThrow(CompletionOtpService::invalid);
        if (actor.getRole() == Role.CUSTOMER) {
            CustomerProfile customer = profiles.customer(actor); if (!request.getCustomer().getId().equals(customer.getId())) throw denied();
        } else if (actor.getRole() == Role.TECHNICIAN) assigned(requestId, profiles.technician(actor));
        else if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN) throw denied();
        return otps.findTopByRequestIdOrderByCreatedAtDesc(requestId).map(o -> state(o, o.verifiedAt != null)).orElse(null);
    }
    private ServiceRequest assigned(Long requestId, TechnicianProfile technician) {
        ServiceRequest request = requests.findById(requestId).orElseThrow(CompletionOtpService::invalid);
        if (!assignments.existsByRequestIdAndTechnicianId(requestId, technician.getId())) throw denied();
        return request;
    }
    private CompletionOtpView state(CompletionOtp otp, boolean verified) {
        LocalDateTime now = LocalDateTime.now(clock);
        String status = verified || otp.verifiedAt != null ? "VERIFIED" : otp.lockedUntil != null && otp.lockedUntil.isAfter(now) ? "LOCKED" : otp.expiresAt.isAfter(now) ? "PENDING" : "EXPIRED";
        return new CompletionOtpView(otp.id, otp.request.getId(), status, otp.expiresAt, otp.attemptsRemaining, otp.lockedUntil, otp.verifiedAt);
    }
    static WorkflowException invalid() { return new WorkflowException(400, "Invalid completion OTP"); }
    static AccessDeniedException denied() { return new AccessDeniedException("Access denied"); }
    record CompletionOtpView(Long id, Long serviceRequestId, String status, LocalDateTime expiresAt, short attemptsRemaining, LocalDateTime lockedUntil, LocalDateTime verifiedAt) {}
}
