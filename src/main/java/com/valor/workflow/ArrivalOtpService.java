package com.valor.workflow;

import com.valor.auth.*;
import com.valor.notifications.NotificationService;
import java.security.SecureRandom;
import java.time.*;
import java.util.EnumSet;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class ArrivalOtpService {
    private static final EnumSet<RequestStatus> ARRIVED_OR_LATER = EnumSet.of(RequestStatus.REACHED_SITE,
            RequestStatus.DIAGNOSIS, RequestStatus.REPAIR_IN_PROGRESS, RequestStatus.WAITING_FOR_PARTS,
            RequestStatus.TESTING, RequestStatus.COMPLETED);
    private final ArrivalOtpRepository otps; private final RequestRepository requests; private final AssignmentRepository assignments;
    private final AssetIdentityAccess identities; private final WorkflowIdentityAccess profiles; private final PasswordEncoder encoder;
    private final NotificationService notifications;
    private final Clock clock; private final SecureRandom random = new SecureRandom();

    ArrivalOtpService(ArrivalOtpRepository otps, RequestRepository requests, AssignmentRepository assignments,
            AssetIdentityAccess identities, WorkflowIdentityAccess profiles, PasswordEncoder encoder, Clock clock, NotificationService notifications) {
        this.otps = otps; this.requests = requests; this.assignments = assignments; this.identities = identities;
        this.profiles = profiles; this.encoder = encoder; this.clock = clock; this.notifications = notifications;
    }

    ArrivalOtpView request(Long requestId) {
        TechnicianProfile technician = profiles.technician(identities.actor());
        ServiceRequest request = assigned(requestId, technician, true);
        if (request.getStatus() != RequestStatus.REACHED_SITE) throw new WorkflowException(409, "Start-work OTP is available after technician arrival");
        ArrivalOtp otp = new ArrivalOtp(); otp.request = request; otp.customer = request.getCustomer(); otp.technician = technician;
        String code = String.valueOf(100000 + random.nextInt(900000));
        otp.otpHash = encoder.encode(code); otp.expiresAt = LocalDateTime.now(clock).plusMinutes(10);
        otp.customerVisibleCode = code; otp.customerVisibleUntil = otp.expiresAt;
        otps.saveAndFlush(otp);
        notifications.createSystem(request.getCustomer().getUser().getId(), "Start-work code ready",
            "Your technician has arrived. Open request #" + request.getId() + " to view your start-work code. Share it only after the technician reaches your site.");
        return state(otp, false);
    }

    @Transactional(noRollbackFor = WorkflowException.class)
    ArrivalOtpView verify(Long requestId, Long otpId, String code) {
        TechnicianProfile technician = profiles.technician(identities.actor());
        ServiceRequest request = assigned(requestId, technician, true);
        if (request.getStatus() != RequestStatus.REACHED_SITE) throw invalid();
        ArrivalOtp otp = otps.lockById(otpId).orElseThrow(ArrivalOtpService::invalid);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!otps.findTopByRequestIdOrderByCreatedAtDescIdDesc(requestId).map(latest -> latest.id.equals(otp.id)).orElse(false)) throw invalid();
        if (!belongsToCurrentAssignment(requestId, otp) || !otp.request.getId().equals(requestId) || !otp.technician.getId().equals(technician.getId()) || otp.verifiedAt != null
                || !otp.expiresAt.isAfter(now) || otp.attemptsRemaining <= 0 || otp.lockedUntil != null && otp.lockedUntil.isAfter(now)) throw invalid();
        if (code == null || !encoder.matches(code, otp.otpHash)) {
            otp.attemptsRemaining--;
            if (otp.attemptsRemaining <= 0) otp.lockedUntil = now.plusMinutes(15);
            throw invalid();
        }
        otp.verifiedAt = now; otp.customerVisibleCode = null; otp.customerVisibleUntil = null;
        return state(otp, true);
    }

    ArrivalOtpView latest(Long requestId) {
        User actor = identities.actor();
        ServiceRequest request = requests.findById(requestId).orElseThrow(ArrivalOtpService::invalid);
        boolean exposeCode = false;
        if (actor.getRole() == Role.CUSTOMER) {
            CustomerProfile customer = profiles.customer(actor); if (!request.getCustomer().getId().equals(customer.getId())) throw denied();
            exposeCode = ARRIVED_OR_LATER.contains(request.getStatus());
        } else if (actor.getRole() == Role.TECHNICIAN) assigned(requestId, profiles.technician(actor), false);
        else if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN) throw denied();
        final boolean canSeeCode = exposeCode;
        return otps.findTopByRequestIdOrderByCreatedAtDescIdDesc(requestId).filter(o -> request.getStatus() == RequestStatus.COMPLETED || belongsToCurrentAssignment(requestId, o)).map(o -> state(o, o.verifiedAt != null, canSeeCode)).orElse(null);
    }

    void requireVerified(Long requestId) {
        ArrivalOtp otp = otps.findTopByRequestIdOrderByCreatedAtDescIdDesc(requestId).orElseThrow(() -> new WorkflowException(409, "Arrival OTP verification is required"));
        if (otp.verifiedAt == null || !belongsToCurrentAssignment(requestId, otp)) throw new WorkflowException(409, "Arrival OTP verification is required");
    }

    private ServiceRequest assigned(Long requestId, TechnicianProfile technician, boolean write) {
        ServiceRequest request = (write ? requests.lockById(requestId) : requests.findById(requestId)).orElseThrow(ArrivalOtpService::invalid);
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
    private boolean belongsToCurrentAssignment(Long requestId, ArrivalOtp otp) {
        return assignments.active(requestId).map(a -> a.getTechnician().getId().equals(otp.technician.getId())
            && !otp.createdAt.isBefore(a.getAssignedAt())).orElse(false);
    }
    private ArrivalOtpView state(ArrivalOtp otp, boolean verified) { return state(otp, verified, false); }
    private ArrivalOtpView state(ArrivalOtp otp, boolean verified, boolean exposeCode) {
        LocalDateTime now = LocalDateTime.now(clock);
        String status = verified || otp.verifiedAt != null ? "VERIFIED" : otp.lockedUntil != null && otp.lockedUntil.isAfter(now) ? "LOCKED" : otp.expiresAt.isAfter(now) ? "PENDING" : "EXPIRED";
        String code = exposeCode && "PENDING".equals(status) && otp.customerVisibleCode != null && otp.customerVisibleUntil != null && otp.customerVisibleUntil.isAfter(now) ? otp.customerVisibleCode : null;
        if (!"PENDING".equals(status) && otp.customerVisibleCode != null) { otp.customerVisibleCode = null; otp.customerVisibleUntil = null; }
        return new ArrivalOtpView(otp.id, otp.request.getId(), status, otp.expiresAt, otp.attemptsRemaining, otp.lockedUntil, otp.verifiedAt, code);
    }
    static WorkflowException invalid() { return new WorkflowException(400, "Invalid arrival OTP"); }
    static AccessDeniedException denied() { return new AccessDeniedException("Access denied"); }
    record ArrivalOtpView(Long id, Long serviceRequestId, String status, LocalDateTime expiresAt, short attemptsRemaining, LocalDateTime lockedUntil, LocalDateTime verifiedAt, String code) {}
}
