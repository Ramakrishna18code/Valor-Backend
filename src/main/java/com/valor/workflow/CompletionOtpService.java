package com.valor.workflow;

import com.valor.auth.*;
import com.valor.notifications.NotificationService;
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
    private final CompletionOtpSender sender; private final NotificationService notifications;
    private final Clock clock; private final SecureRandom random = new SecureRandom();
    private final boolean enabled;
    CompletionOtpService(CompletionOtpRepository otps, RequestRepository requests, AssignmentRepository assignments,
            AssetIdentityAccess identities, WorkflowIdentityAccess profiles, PasswordEncoder encoder, CompletionOtpSender sender, Clock clock, NotificationService notifications,
            @Value("${valor.completion-otp.enabled:true}") boolean enabled) {
        this.otps = otps; this.requests = requests; this.assignments = assignments; this.identities = identities; this.profiles = profiles;
        this.encoder = encoder; this.sender = sender; this.clock = clock; this.enabled = enabled; this.notifications = notifications;
    }
    CompletionOtpView request(Long requestId) {
        TechnicianProfile technician = profiles.technician(identities.actor());
        ServiceRequest request = assigned(requestId, technician, true);
        if (request.getStatus() != RequestStatus.TESTING) throw new WorkflowException(409, "Completion OTP is available during final testing");
        CompletionOtp otp = new CompletionOtp(); otp.request = request; otp.customer = request.getCustomer(); otp.technician = technician;
        String code = String.valueOf(100000 + random.nextInt(900000));
        otp.otpHash = encoder.encode(code); otp.expiresAt = LocalDateTime.now(clock).plusMinutes(10);
        otp.customerVisibleCode = code; otp.customerVisibleUntil = otp.expiresAt;
        otps.saveAndFlush(otp);
        notifications.createSystem(request.getCustomer().getUser().getId(), "Completion code ready",
            "Your technician is ready for handover. Open service " + request.getServiceId() + " to view your completion code. Share it only after the work is finished."); sender.sendCompletionOtp(request, code);
        return state(otp, false);
    }
    @Transactional(noRollbackFor = WorkflowException.class)
    CompletionOtpView verify(Long requestId, Long otpId, String code) {
        TechnicianProfile technician = profiles.technician(identities.actor());
        ServiceRequest request = assigned(requestId, technician, true);
        if (request.getStatus() != RequestStatus.TESTING) throw invalid();
        CompletionOtp otp = otps.lockById(otpId).orElseThrow(CompletionOtpService::invalid);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!otps.findTopByRequestIdOrderByCreatedAtDescIdDesc(requestId).map(latest -> latest.id.equals(otp.id)).orElse(false)) throw invalid();
        if (!belongsToCurrentAssignment(requestId, otp) || !otp.request.getId().equals(requestId) || !otp.technician.getId().equals(technician.getId()) || otp.verifiedAt != null
                || !otp.expiresAt.isAfter(now) || otp.attemptsRemaining <= 0 || otp.lockedUntil != null && otp.lockedUntil.isAfter(now)) throw invalid();
        if (code == null || !encoder.matches(code, otp.otpHash)) {
            otp.attemptsRemaining--;
            if (otp.attemptsRemaining <= 0) otp.lockedUntil = now.plusMinutes(15);
            throw invalid();
        }
        otp.verifiedAt = now;
        otp.customerVisibleCode = null; otp.customerVisibleUntil = null;
        return state(otp, true);
    }
    void requireVerified(Long requestId) {
        if (!enabled) return;
        CompletionOtp otp = otps.findTopByRequestIdOrderByCreatedAtDescIdDesc(requestId).orElseThrow(() -> new WorkflowException(409, "Completion OTP verification required"));
        if (otp.verifiedAt == null || !belongsToCurrentAssignment(requestId, otp)) throw new WorkflowException(409, "Completion OTP verification required");
    }
    CompletionOtpView latest(Long requestId) {
        User actor = identities.actor();
        ServiceRequest request = requests.findById(requestId).orElseThrow(CompletionOtpService::invalid);
        boolean exposeCode = false;
        if (actor.getRole() == Role.CUSTOMER) {
            CustomerProfile customer = profiles.customer(actor); if (!request.getCustomer().getId().equals(customer.getId())) throw denied();
            exposeCode = true;
        } else if (actor.getRole() == Role.TECHNICIAN) assigned(requestId, profiles.technician(actor), false);
        else if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN) throw denied();
        final boolean customerCanSeeCode = exposeCode;
        return otps.findTopByRequestIdOrderByCreatedAtDescIdDesc(requestId).filter(o -> request.getStatus() == RequestStatus.COMPLETED || belongsToCurrentAssignment(requestId, o)).map(o -> state(o, o.verifiedAt != null, customerCanSeeCode && request.getStatus() == RequestStatus.TESTING)).orElse(null);
    }
    private ServiceRequest assigned(Long requestId, TechnicianProfile technician, boolean write) {
        ServiceRequest request = (write ? requests.lockById(requestId) : requests.findById(requestId)).orElseThrow(CompletionOtpService::invalid);
        var assignment = assignments.active(requestId).orElse(null);
        if (assignment == null) {
            if (!write && (request.getStatus() == RequestStatus.COMPLETED || request.getStatus() == RequestStatus.CANCELLED)
                    && assignments.existsByRequestIdAndTechnicianId(requestId, technician.getId())) return request;
            throw denied();
        }
        if (!assignment.getTechnician().getId().equals(technician.getId())) throw denied();
        if (write && assignment.getStatus() != AssignmentStatus.ACCEPTED) throw new WorkflowException(409, "Accept your assignment before requesting or verifying a code");
        return request;
    }
    private boolean belongsToCurrentAssignment(Long requestId, CompletionOtp otp) {
        return assignments.active(requestId).map(a -> a.getTechnician().getId().equals(otp.technician.getId())
            && !otp.createdAt.isBefore(a.getAssignedAt())).orElse(false);
    }
    private CompletionOtpView state(CompletionOtp otp, boolean verified) { return state(otp, verified, false); }
    private CompletionOtpView state(CompletionOtp otp, boolean verified, boolean exposeCode) {
        LocalDateTime now = LocalDateTime.now(clock);
        String status = verified || otp.verifiedAt != null ? "VERIFIED" : otp.lockedUntil != null && otp.lockedUntil.isAfter(now) ? "LOCKED" : otp.expiresAt.isAfter(now) ? "PENDING" : "EXPIRED";
        String code = exposeCode && "PENDING".equals(status) && otp.customerVisibleCode != null && otp.customerVisibleUntil != null && otp.customerVisibleUntil.isAfter(now) ? otp.customerVisibleCode : null;
        if (!"PENDING".equals(status) && otp.customerVisibleCode != null) { otp.customerVisibleCode = null; otp.customerVisibleUntil = null; }
        return new CompletionOtpView(otp.id, otp.request.getId(), status, otp.expiresAt, otp.attemptsRemaining, otp.lockedUntil, otp.verifiedAt, code);
    }
    static WorkflowException invalid() { return new WorkflowException(400, "Invalid completion OTP"); }
    static AccessDeniedException denied() { return new AccessDeniedException("Access denied"); }
    record CompletionOtpView(Long id, Long serviceRequestId, String status, LocalDateTime expiresAt, short attemptsRemaining, LocalDateTime lockedUntil, LocalDateTime verifiedAt, String code) {}
}
