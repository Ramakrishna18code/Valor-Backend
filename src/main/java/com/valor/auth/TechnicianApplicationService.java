package com.valor.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;

@Service
@Transactional
class TechnicianApplicationService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "OTP_VERIFIED", "SUBMITTED", "DOCUMENTS_PENDING", "UNDER_DOCUMENT_REVIEW", "DOCUMENTS_REJECTED", "DOCUMENTS_VERIFIED", "MEETING_REQUIRED", "MEETING_SCHEDULED", "MEETING_COMPLETED", "APPROVED", "REJECTED", "SUSPENDED");
    private static final Set<String> REQUIRED_DOCUMENTS = Set.of("PROFILE_PHOTO", "AADHAAR_CARD", "ADDRESS_PROOF");
    private static final Set<String> DOCUMENT_TYPES = Set.of("PROFILE_PHOTO", "AADHAAR_CARD", "DRIVING_LICENSE", "EDUCATIONAL_CERTIFICATE", "EXPERIENCE_CERTIFICATE", "TECHNICAL_CERTIFICATION", "ADDRESS_PROOF", "MEDICAL_FITNESS_CERTIFICATE");
    private static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");
    private static final long MAX_DOCUMENT_BYTES = 5L * 1024L * 1024L;
    private final TechnicianApplicationRepository applications;
    private final TechnicianApplicationDocumentRepository documents;
    private final UserRepo users;
    private final TechRepo technicians;
    private final PasswordEncoder encoder;
    private final TechnicianApplicationStorage storage;
    private final AssetIdentityAccess identities;
    private final AuditService audit;

    TechnicianApplicationService(TechnicianApplicationRepository applications, TechnicianApplicationDocumentRepository documents,
            UserRepo users, TechRepo technicians, PasswordEncoder encoder, TechnicianApplicationStorage storage,
            AssetIdentityAccess identities, AuditService audit) {
        this.applications = applications; this.documents = documents; this.users = users; this.technicians = technicians;
        this.encoder = encoder; this.storage = storage; this.identities = identities; this.audit = audit;
    }

    Created create(TechnicianApplicationController.Create input) {
        String email = normalizeEmail(input.email());
        String phone = normalizePhone(input.phone());
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || phone.length() < 10) throw new IllegalArgumentException("Enter a valid email and mobile number");
        if (users.findByEmail(email).isPresent() || applications.existsByEmailIgnoreCaseAndStatusIn(email, Set.of("DRAFT", "OTP_VERIFIED", "SUBMITTED", "UNDER_REVIEW", "APPROVED")))
            throw new IllegalArgumentException("An application already exists for this email");
        if (input.password() == null || input.password().isBlank() || input.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Enter a valid password");
        TechnicianApplication row = new TechnicianApplication();
        String token = newToken();
        row.tokenHash = hash(token);
        row.fullName = required(input.fullName(), "Full name is required"); row.email = email; row.phone = phone;
        row.passwordHash = encoder.encode(input.password());
        applications.saveAndFlush(row);
        return new Created(view(row), token);
    }

    OtpView sendOtp(Long id, String token) {
        TechnicianApplication row = access(id, token);
        String code = "1111";
        row.otpHash = encoder.encode(code); row.otpExpiresAt = LocalDateTime.now().plusMinutes(10); row.otpAttemptsRemaining = 3;
        return new OtpView(row.id, row.otpExpiresAt, true, code);
    }

    ApplicationView verifyOtp(Long id, String token, String otp) {
        TechnicianApplication row = access(id, token);
        if (row.otpHash == null || row.otpExpiresAt == null || row.otpExpiresAt.isBefore(LocalDateTime.now()) || row.otpAttemptsRemaining <= 0 || !encoder.matches(otp, row.otpHash)) {
            row.otpAttemptsRemaining = Math.max(0, row.otpAttemptsRemaining - 1); throw new IllegalArgumentException("The OTP is invalid or expired");
        }
        row.otpVerifiedAt = LocalDateTime.now(); row.status = "OTP_VERIFIED"; return view(row);
    }

    ApplicationView update(Long id, String token, TechnicianApplicationController.Update input) {
        TechnicianApplication row = access(id, token);
        if (input.experience() != null) row.experience = input.experience(); if (input.specialization() != null) row.specialization = input.specialization();
        if (input.liftBrands() != null) row.liftBrands = input.liftBrands(); if (input.certifications() != null) row.certifications = input.certifications();
        if (input.highestQualification() != null) row.highestQualification = input.highestQualification(); if (input.handsOnExperience() != null) row.handsOnExperience = input.handsOnExperience();
        if (input.preferredLocations() != null) row.preferredLocations = input.preferredLocations(); if (input.willingToWorkAtHeights() != null) row.willingToWorkAtHeights = input.willingToWorkAtHeights();
        if (input.travelAvailability() != null) row.travelAvailability = input.travelAvailability(); if (input.additionalNotes() != null) row.additionalNotes = input.additionalNotes();
        if (input.aadhaarNumber() != null) row.aadhaarNumber = input.aadhaarNumber().trim(); if (input.drivingLicenseNumber() != null) row.drivingLicenseNumber = input.drivingLicenseNumber().trim();
        return view(row);
    }

    DocumentView uploadDocument(Long id, String token, String documentType, MultipartFile file) {
        TechnicianApplication row = access(id, token); validateDocument(documentType, file);
        try {
            String type = file.getContentType().toLowerCase(Locale.ROOT); String key = storage.store(extension(type), file.getInputStream());
            TechnicianApplicationDocument document = documents.findByApplicationIdAndDocumentType(id, documentType).orElseGet(TechnicianApplicationDocument::new);
            document.application = row; document.documentType = documentType; document.originalFilename = cleanName(file.getOriginalFilename());
            document.contentType = type; document.fileSize = file.getSize(); document.storageKey = key;
            document.reviewStatus = "PENDING"; document.reviewerUserId = null; document.reviewReason = null; document.reviewedAt = null;
            if (Set.of("DOCUMENTS_REJECTED", "DOCUMENTS_PENDING").contains(row.status)) row.status = "DOCUMENTS_PENDING";
            return documentView(documents.saveAndFlush(document));
        } catch (IOException ex) { throw new IllegalStateException("Document could not be saved", ex); }
    }

    ApplicationView submit(Long id, String token) {
        TechnicianApplication row = access(id, token);
        if (row.otpVerifiedAt == null) throw new IllegalArgumentException("Verify your mobile number first");
        if (row.fullName == null || row.experience == null || row.specialization == null)
            throw new IllegalArgumentException("Complete your professional details before submitting");
        row.status = "DOCUMENTS_PENDING"; return view(row);
    }

    ApplicationView read(Long id, String token) { return view(access(id, token)); }
    record Download(Resource resource, String contentType) {}
    Download downloadDocument(Long applicationId, Long documentId) {
        admin(); TechnicianApplicationDocument document=documents.findById(documentId).filter(d -> d.application.id.equals(applicationId)).orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return new Download(storage.load(document.storageKey), document.contentType);
    }
    List<ApplicationView> adminList(int page, int size) { admin(); return applications.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 100))).map(this::view).getContent(); }
    ApplicationView adminStatus(Long id, String status) {
        admin(); TechnicianApplication row=application(id); String target=status.trim().toUpperCase(Locale.ROOT);
        if ("UNDER_REVIEW".equals(target) && !Set.of("DOCUMENTS_PENDING","SUBMITTED").contains(row.status)) throw new IllegalArgumentException("Application is not ready for document review");
        if ("APPROVED".equals(target)) finalApprove(row);
        else if ("REJECTED".equals(target) || "SUSPENDED".equals(target)) { row.status=target; row.reviewedAt=LocalDateTime.now(); row.reviewedBy=identities.actor(); }
        else if ("UNDER_REVIEW".equals(target)) { row.status=target; row.reviewedAt=LocalDateTime.now(); row.reviewedBy=identities.actor(); }
        else throw new IllegalArgumentException("Use the verification actions for this state");
        return view(row);
    }

    ApplicationView reviewDocument(Long applicationId, Long documentId, String status, String reason) {
        admin(); TechnicianApplication row=application(applicationId); TechnicianApplicationDocument document=documents.findById(documentId).filter(d -> d.application.id.equals(applicationId)).orElseThrow(() -> new IllegalArgumentException("Document not found"));
        String target=status.trim().toUpperCase(Locale.ROOT); if (!Set.of("VERIFIED","REJECTED").contains(target)) throw new IllegalArgumentException("Invalid document review status");
        if ("REJECTED".equals(target) && (reason==null || reason.isBlank())) throw new IllegalArgumentException("Rejection reason is required");
        document.reviewStatus=target; document.reviewerUserId=identities.actor().getId(); document.reviewReason=reason; document.reviewedAt=LocalDateTime.now(); documents.save(document);
        if ("REJECTED".equals(target)) row.status="DOCUMENTS_REJECTED"; else { var required=documents.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream().filter(d -> REQUIRED_DOCUMENTS.contains(d.documentType)).toList(); if (required.stream().map(d -> d.documentType).collect(java.util.stream.Collectors.toSet()).containsAll(REQUIRED_DOCUMENTS) && required.stream().allMatch(d -> "VERIFIED".equals(d.reviewStatus))) row.status="DOCUMENTS_VERIFIED"; }
        audit.record("TECHNICIAN_DOCUMENT_"+target,"TECHNICIAN_APPLICATION_DOCUMENT",documentId,"Technician document review"); return view(row);
    }

    ApplicationView meeting(Long id, TechnicianApplicationController.Meeting input) {
        admin(); TechnicianApplication row=application(id); if (!"DOCUMENTS_VERIFIED".equals(row.status)) throw new IllegalArgumentException("Documents must be verified first");
        String mode=input.mode().trim().toUpperCase(Locale.ROOT); if (!Set.of("NONE","GOOGLE_MEET","PHONE","PHYSICAL").contains(mode)) throw new IllegalArgumentException("Invalid meeting mode");
        if (!"NONE".equals(mode) && input.meetingAt() == null) throw new IllegalArgumentException("Meeting date and time are required");
        if ("GOOGLE_MEET".equals(mode) && (input.meetingUrl() == null || input.meetingUrl().isBlank())) throw new IllegalArgumentException("Google Meet URL is required");
        if ("PHONE".equals(mode) && (input.meetingPhone() == null || input.meetingPhone().isBlank())) throw new IllegalArgumentException("Meeting phone is required");
        if ("PHYSICAL".equals(mode) && (input.meetingLocation() == null || input.meetingLocation().isBlank())) throw new IllegalArgumentException("Meeting location is required");
        row.meetingRequired=!"NONE".equals(mode); row.meetingMode=mode; row.meetingAt=input.meetingAt(); row.meetingUrl=input.meetingUrl(); row.meetingPhone=input.meetingPhone(); row.meetingLocation=input.meetingLocation(); row.meetingLatitude=input.meetingLatitude(); row.meetingLongitude=input.meetingLongitude(); row.meetingMapUrl=input.meetingMapUrl(); row.meetingNotes=input.notes();
        row.status=row.meetingRequired ? "MEETING_SCHEDULED" : "DOCUMENTS_VERIFIED"; audit.record("TECHNICIAN_MEETING_UPDATE","TECHNICIAN_APPLICATION",id,"Technician meeting updated"); return view(row);
    }

    ApplicationView completeMeeting(Long id, String notes) { admin(); TechnicianApplication row=application(id); if (!"MEETING_SCHEDULED".equals(row.status) && !"MEETING_REQUIRED".equals(row.status)) throw new IllegalArgumentException("Meeting is not scheduled"); row.status="MEETING_COMPLETED"; row.meetingCompletedAt=LocalDateTime.now(); row.meetingCompletedByUserId=identities.actor().getId(); row.meetingNotes=notes; audit.record("TECHNICIAN_MEETING_COMPLETED","TECHNICIAN_APPLICATION",id,"Technician meeting completed"); return view(row); }

    private void finalApprove(TechnicianApplication row) { if ("DOCUMENTS_VERIFIED".equals(row.status) && !row.meetingRequired) {} else if (!"MEETING_COMPLETED".equals(row.status)) throw new IllegalArgumentException("Required verification steps are incomplete"); row.status="APPROVED"; row.finalApprovedAt=LocalDateTime.now(); row.finalApprovedByUserId=identities.actor().getId(); row.reviewedAt=row.finalApprovedAt; row.reviewedBy=identities.actor(); provision(row); audit.record("TECHNICIAN_FINAL_APPROVED","TECHNICIAN_APPLICATION",row.id,"Technician application approved"); }
    private TechnicianApplication application(Long id) { return applications.findById(id).orElseThrow(() -> new IllegalArgumentException("Application not found")); }

    private void provision(TechnicianApplication application) {
        if (users.findByEmail(application.email).isPresent()) throw new IllegalArgumentException("A user already exists for this email");
        User user = new User(); user.setEmail(application.email); user.setPhone(application.phone); user.setPasswordHash(application.passwordHash); user.setRole(Role.TECHNICIAN); users.saveAndFlush(user);
        TechnicianProfile profile = new TechnicianProfile(); profile.user = user; profile.specialization = application.specialization; profile.assignedArea = application.preferredLocations; profile.availabilityStatus = "AVAILABLE"; technicians.saveAndFlush(profile);
    }

    private TechnicianApplication admin() { User actor = identities.actor(); if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN) throw new AccessDeniedException("Access denied"); return null; }
    private TechnicianApplication access(Long id, String token) { if (token == null || token.isBlank()) throw new AccessDeniedException("Application access token required"); return applications.findById(id).filter(row -> row.tokenHash.equals(hash(token))).orElseThrow(() -> new AccessDeniedException("Application access denied")); }
    private String newToken() { return UUID.randomUUID() + UUID.randomUUID().toString(); }
    private String normalizeEmail(String value) { return required(value, "Email is required").trim().toLowerCase(Locale.ROOT); }
    private String normalizePhone(String value) { return required(value, "Mobile number is required").replaceAll("[^0-9+]", ""); }
    private String required(String value, String message) { if (value == null || value.isBlank()) throw new IllegalArgumentException(message); return value.trim(); }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private ApplicationView view(TechnicianApplication row) { return new ApplicationView(row.id,row.fullName,row.email,row.phone,row.experience,row.specialization,row.liftBrands,row.certifications,row.highestQualification,row.handsOnExperience,row.preferredLocations,row.willingToWorkAtHeights,row.travelAvailability,row.additionalNotes,row.aadhaarNumber,row.drivingLicenseNumber,row.status,row.otpVerifiedAt,row.createdAt,row.updatedAt,row.meetingRequired,row.meetingMode,row.meetingAt,row.meetingUrl,row.meetingPhone,row.meetingLocation,row.meetingLatitude,row.meetingLongitude,row.meetingMapUrl,row.meetingNotes,row.meetingCompletedAt,documents.findByApplicationIdOrderByCreatedAtAsc(row.id).stream().map(this::documentView).toList()); }
    private DocumentView documentView(TechnicianApplicationDocument row) { return new DocumentView(row.id,row.documentType,row.originalFilename,row.contentType,row.fileSize,row.createdAt,row.reviewStatus,row.reviewReason,row.reviewedAt); }
    private void validateDocument(String type, MultipartFile file) { if (!DOCUMENT_TYPES.contains(type)) throw new IllegalArgumentException("Unsupported document type"); if (file == null || file.isEmpty() || file.getSize() > MAX_DOCUMENT_BYTES) throw new IllegalArgumentException("Document must be smaller than 5 MB"); if (!CONTENT_TYPES.contains(Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Use JPG, PNG, or PDF"); }
    private String extension(String type) { return switch (type) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; default -> ".pdf"; }; }
    private String cleanName(String value) { String name = value == null || value.isBlank() ? "document" : value.replaceAll("[\\\\/\\r\\n]", "_"); return name.length() > 255 ? name.substring(name.length() - 255) : name; }
    record Created(ApplicationView application, String applicationToken) {}
    record OtpView(Long applicationId, LocalDateTime expiresAt, boolean developmentOnly, String otp) {}
    record ApplicationView(Long id,String fullName,String email,String phone,String experience,String specialization,String liftBrands,String certifications,String highestQualification,String handsOnExperience,String preferredLocations,Boolean willingToWorkAtHeights,String travelAvailability,String additionalNotes,String aadhaarNumber,String drivingLicenseNumber,String status,LocalDateTime otpVerifiedAt,LocalDateTime createdAt,LocalDateTime updatedAt,boolean meetingRequired,String meetingMode,LocalDateTime meetingAt,String meetingUrl,String meetingPhone,String meetingLocation,java.math.BigDecimal meetingLatitude,java.math.BigDecimal meetingLongitude,String meetingMapUrl,String meetingNotes,LocalDateTime meetingCompletedAt,List<DocumentView> documents) {}
    record DocumentView(Long id,String documentType,String originalFilename,String contentType,Long fileSize,LocalDateTime createdAt,String reviewStatus,String reviewReason,LocalDateTime reviewedAt) {}
}
