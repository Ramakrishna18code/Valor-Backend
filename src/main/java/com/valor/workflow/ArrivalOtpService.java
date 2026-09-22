package com.valor.workflow;

import com.valor.auth.*;
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
    private final Clock clock; private final SecureRandom random = new SecureRandom();

    ArrivalOtpService(ArrivalOtpRepository otps, RequestRepository requests, AssignmentRepository assignments,
            AssetIdentityAccess identities, WorkflowIdentityAccess profiles, PasswordEncoder encoder, Clock clock) {
        this.otps = otps; this.requests = requests; this.assignments = assignments; this.identities = identities;
        this.profiles = profiles; this.encoder = encoder; this.clock = clock;
    }

    ArrivalOtpView request(Long requestId) {
        TechnicianProfile technician = profiles.technician(identities.actor());
        ServiceRequest request = assigned(requestId, technician);
        if (!ARRIVED_OR_LATER.contains(request.getStatus())) throw new WorkflowException(409, "Arrival OTP is available after technician arrival");
        ArrivalOtp otp = new ArrivalOtp(); otp.request = request; otp.customer = request.getCustomer(); otp.technician = technician;
        String code = String.valueOf(100000 + random.nextInt(900000));
        otp.otpHash = encoder.encode(code); otp.expiresAt = LocalDateTime.now(clock).plusMinutes(10);
        otp.customerVisibleCode = code; otp.customerVisibleUntil = otp.expiresAt;
        otps.saveAndFlush(otp);
        return state(otp, false);
    }

    ArrivalOtpView verify(Long requestId, Long otpId, String code) {
        TechnicianProfile technician = profiles.technician(identities.actor());
        assigned(requestId, technician);
        ArrivalOtp otp = otps.lockById(otpId).orElseThrow(ArrivalOtpService::invalid);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!otp.request.getId().equals(requestId) || !otp.technician.getId().equals(technician.getId()) || otp.verifiedAt != null
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
        } else if (actor.getRole() == Role.TECHNICIAN) assigned(requestId, profiles.technician(actor));
        else if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN) throw denied();
        final boolean canSeeCode = exposeCode;
        return otps.findTopByRequestIdOrderByCreatedAtDesc(requestId).map(o -> state(o, o.verifiedAt != null, canSeeCode)).orElse(null);
    }

    private ServiceRequest assigned(Long requestId, TechnicianProfile technician) {
        ServiceRequest request = requests.findById(requestId).orElseThrow(ArrivalOtpService::invalid);
        if (!assignments.existsByRequestIdAndTechnicianId(requestId, technician.getId())) throw denied();
        return request;
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
