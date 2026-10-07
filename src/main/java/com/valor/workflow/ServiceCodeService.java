package com.valor.workflow;

import com.valor.assets.Lift;
import com.valor.auth.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class ServiceCodeService {
    private static final int MAX_ATTEMPTS = 3;
    private final LiftServiceCodeRepository codes;
    private final WorkflowLiftRepository liftRepository;
    private final ServiceCodeVerificationRepository verifications;
    private final RequestRepository requests;
    private final AssignmentRepository assignments;
    private final AssetIdentityAccess identities;
    private final WorkflowIdentityAccess profiles;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();
    private final byte[] encryptionKey;

    ServiceCodeService(LiftServiceCodeRepository codes, ServiceCodeVerificationRepository verifications,
            RequestRepository requests, AssignmentRepository assignments, AssetIdentityAccess identities,
            WorkflowIdentityAccess profiles, PasswordEncoder encoder,
            WorkflowLiftRepository liftRepository, @Value("${valor.service-code.secret:change-this-service-code-secret}") String secret) {
        this.codes = codes; this.verifications = verifications; this.requests = requests;
        this.assignments = assignments; this.identities = identities; this.profiles = profiles; this.encoder = encoder;
        this.liftRepository = liftRepository;
        try { this.encryptionKey = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception error) { throw new IllegalStateException("Unable to initialize service-code encryption", error); }
    }

    CodeView customerCode(Long liftId, boolean regenerate) {
        User actor = identities.actor();
        CustomerProfile customer = profiles.customer(actor);
        LiftServiceCode code = codes.lockByLiftId(liftId).orElse(null);
        if (code != null && !code.customer.getId().equals(customer.getId())) throw denied();
        if (code == null) {
            Lift lift = liftRepository.findById(liftId).orElseThrow(() -> new WorkflowException(404, "Lift not found"));
            if (!lift.getBuilding().getCustomer().getId().equals(customer.getId())) throw denied();
            String value = generate();
            code = new LiftServiceCode(); code.lift = lift; code.customer = customer; code.codeHash = encoder.encode(value); code.codeCiphertext = encrypt(value);
            codes.saveAndFlush(code);
            return new CodeView(liftId, value, "AVAILABLE", null, 0, null);
        }
        if (regenerate) { String value = generate(); code.codeHash = encoder.encode(value); code.codeCiphertext = encrypt(value); return new CodeView(liftId, value, "AVAILABLE", null, 0, null); }
        return new CodeView(code.lift.getId(), reveal(code), "AVAILABLE", null, 0, null);
    }

    String ensureCustomerCode(Lift lift, CustomerProfile customer) {
        LiftServiceCode code = codes.findByLiftId(lift.getId()).orElse(null);
        if (code == null) {
            code = new LiftServiceCode(); code.lift = lift; code.customer = customer;
            String value = generate(); code.codeHash = encoder.encode(value); code.codeCiphertext = encrypt(value);
            codes.saveAndFlush(code);
            return value;
        }
        return null;
    }

    CodeView stateForRequest(Long requestId, ServiceCodeAction action, boolean exposeCode) {
        ServiceRequest request = requests.findById(requestId).orElseThrow(() -> new WorkflowException(404, "Service request not found"));
        if (request.getLift() == null) return null;
        LiftServiceCode code = codes.findByLiftId(request.getLift().getId()).orElse(null);
        if (code == null && exposeCode) { ensureCustomerCode(request.getLift(), request.getCustomer()); code = codes.findByLiftId(request.getLift().getId()).orElse(null); }
        if (code == null) return new CodeView(request.getLift().getId(), null, "UNAVAILABLE", null, null, null);
        ServiceCodeVerification verification = verifications.findTopByRequestIdAndActionOrderByCreatedAtDescIdDesc(requestId, action).orElse(null);
        String status = verification == null || verification.verifiedAt == null ? "AVAILABLE" : "VERIFIED";
        if (verification != null && verification.lockedUntil != null && verification.lockedUntil.isAfter(LocalDateTime.now())) status = "LOCKED";
        return new CodeView(code.lift.getId(), exposeCode ? reveal(code) : null, status,
                verification == null ? null : verification.lockedUntil, verification == null ? 0 : (int) verification.attempts, verification == null ? null : verification.verifiedAt);
    }

    CodeView verify(Long requestId, ServiceCodeAction action, String submitted) {
        User actor = identities.actor();
        TechnicianProfile technician = profiles.technician(actor);
        ServiceRequest request = requests.lockById(requestId).orElseThrow(() -> new WorkflowException(404, "Service request not found"));
        if (request.getLift() == null) throw new WorkflowException(409, "Installation requests do not use a lift service code");
        var assignment = assignments.active(requestId).orElseThrow(() -> new WorkflowException(409, "Active assignment required"));
        if (!assignment.getTechnician().getId().equals(technician.getId())) throw denied();
        if ((action == ServiceCodeAction.START && request.getStatus() != RequestStatus.REACHED_SITE)
                || (action == ServiceCodeAction.COMPLETE && request.getStatus() != RequestStatus.TESTING)) throw new WorkflowException(409, "Verification is not available in this state");
        LiftServiceCode code = codes.lockByLiftId(request.getLift().getId()).orElseThrow(() -> new WorkflowException(409, "Customer lift code is not configured"));
        ServiceCodeVerification verification = verifications.lock(requestId, technician.getId(), action).orElseGet(() -> {
            ServiceCodeVerification created = new ServiceCodeVerification(); created.request = request; created.code = code; created.technician = technician; created.action = action; return verifications.save(created);
        });
        LocalDateTime now = LocalDateTime.now();
        if (verification.verifiedAt != null) return stateForRequest(requestId, action, false);
        if (verification.lockedUntil != null && verification.lockedUntil.isAfter(now)) throw new WorkflowException(429, "Verification is temporarily locked");
        if (submitted == null || !encoder.matches(submitted, code.codeHash)) {
            verification.attempts++;
            if (verification.attempts >= MAX_ATTEMPTS) verification.lockedUntil = now.plusMinutes(15);
            throw new WorkflowException(400, "Incorrect service code");
        }
        verification.verifiedAt = now;
        return stateForRequest(requestId, action, false);
    }

    void requireVerified(Long requestId, ServiceCodeAction action) {
        ServiceRequest request = requests.findById(requestId).orElseThrow(() -> new WorkflowException(404, "Service request not found"));
        if (request.getLift() == null) return;
        TechnicianProfile technician = profiles.technician(identities.actor());
        ServiceCodeVerification verification = verifications.findTopByRequestIdAndActionOrderByCreatedAtDescIdDesc(requestId, action).orElse(null);
        if (verification == null || verification.verifiedAt == null || !verification.technician.getId().equals(technician.getId())) throw new WorkflowException(409, "Service code verification is required");
    }

    private String reveal(LiftServiceCode code) {
        try {
            byte[] packed = Base64.getDecoder().decode(code.codeCiphertext);
            byte[] iv = java.util.Arrays.copyOfRange(packed, 0, 12);
            byte[] payload = java.util.Arrays.copyOfRange(packed, 12, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        } catch (Exception error) { throw new IllegalStateException("Unable to read service code", error); }
    }
    private String encrypt(String value) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] payload = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + payload.length]; System.arraycopy(iv, 0, packed, 0, iv.length); System.arraycopy(payload, 0, packed, iv.length, payload.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (Exception error) { throw new IllegalStateException("Unable to protect service code", error); }
    }
    private String generate() { return String.format("%06d", 100000 + random.nextInt(900000)); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Access denied"); }
    record CodeView(Long liftId, String code, String status, LocalDateTime lockedUntil, Integer attempts, LocalDateTime verifiedAt) {}
}
