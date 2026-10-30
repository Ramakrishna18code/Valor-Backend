package com.valor.auth;

import com.valor.communication.EmailEventService;
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
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.Resource;

@Service
@Transactional
class TechnicianApplicationService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "OTP_VERIFIED", "SUBMITTED", "DOCUMENTS_PENDING", "UNDER_DOCUMENT_REVIEW", "DOCUMENTS_REJECTED", "DOCUMENTS_VERIFIED", "MEETING_REQUIRED", "MEETING_SCHEDULED", "MEETING_COMPLETED", "APPROVED", "REJECTED", "SUSPENDED");
    private static final Set<String> REQUIRED_DOCUMENTS = Set.of("PROFILE_PHOTO", "ADDRESS_PROOF");
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
    private final Environment environment;
    private final AuditService audit;
    private final EmailEventService emails;

    TechnicianApplicationService(TechnicianApplicationRepository applications, TechnicianApplicationDocumentRepository documents,
            UserRepo users, TechRepo technicians, PasswordEncoder encoder, TechnicianApplicationStorage storage,
            AssetIdentityAccess identities, Environment environment, AuditService audit, EmailEventService emails) {
        this.applications = applications; this.documents = documents; this.users = users; this.technicians = technicians;
        this.encoder = encoder; this.storage = storage; this.identities = identities;
        this.environment = environment; this.audit = audit; this.emails = emails;
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

    Created statusLogin(String email, String password) {
        TechnicianApplication row = applications.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new IllegalArgumentException("Application status credentials are invalid"));
        if (!encoder.matches(password, row.passwordHash)) throw new IllegalArgumentException("Application status credentials are invalid");
        if ("APPROVED".equals(row.status)) throw new IllegalArgumentException("Use technician sign in for an approved account");
        String token = newToken(); row.tokenHash = hash(token); applications.save(row);
        return new Created(view(row), token);
    }

    OtpView sendOtp(Long id, String token) {
        requireDevelopmentOtp();
        TechnicianApplication row = access(id, token);
        requireDraft(row);
        var now = LocalDateTime.now();
        if (row.otpExpiresAt != null && row.otpExpiresAt.isAfter(now)) {
            if (row.otpAttemptsRemaining <= 0 || row.otpExpiresAt.minusMinutes(9).isAfter(now))
                throw new IllegalArgumentException("OTP unavailable; wait before trying again");
        } else row.otpAttemptsRemaining = 3;
        String code = String.format("%04d", new java.security.SecureRandom().nextInt(10000));
        row.otpHash = encoder.encode(code); row.otpExpiresAt = now.plusMinutes(10);
        return new OtpView(row.id, row.otpExpiresAt, true, code);
    }

    @Transactional(dontRollbackOn = IllegalArgumentException.class)
    ApplicationView verifyOtp(Long id, String token, String otp) {
        requireDevelopmentOtp();
        TechnicianApplication row = access(id, token);
        requireDraft(row);
        if (row.otpHash == null || row.otpExpiresAt == null || !row.otpExpiresAt.isAfter(LocalDateTime.now()) || row.otpAttemptsRemaining <= 0 || !encoder.matches(otp, row.otpHash)) {
            row.otpAttemptsRemaining = Math.max(0, row.otpAttemptsRemaining - 1); throw new IllegalArgumentException("The OTP is invalid or expired");
        }
        row.otpVerifiedAt = LocalDateTime.now(); row.status = "OTP_VERIFIED"; return view(row);
    }

    ApplicationView update(Long id, String token, TechnicianApplicationController.Update input) {
        TechnicianApplication row = access(id, token);
        requireEditable(row);
        validatePreferredLocations(input.preferredLocations());
        validateAadhaar(input.aadhaarNumber());
        validateDrivingLicense(input.drivingLicenseNumber());
        if (input.experience() != null) row.experience = input.experience(); if (input.specialization() != null) row.specialization = input.specialization();
        if (input.liftBrands() != null) row.liftBrands = input.liftBrands(); if (input.certifications() != null) row.certifications = input.certifications();
        if (input.highestQualification() != null) row.highestQualification = input.highestQualification(); if (input.handsOnExperience() != null) row.handsOnExperience = input.handsOnExperience();
        if (input.preferredLocations() != null) row.preferredLocations = input.preferredLocations(); if (input.willingToWorkAtHeights() != null) row.willingToWorkAtHeights = input.willingToWorkAtHeights();
        if (input.travelAvailability() != null) row.travelAvailability = input.travelAvailability(); if (input.additionalNotes() != null) row.additionalNotes = input.additionalNotes();
        if (input.aadhaarNumber() != null) row.aadhaarNumber = input.aadhaarNumber().replaceAll("\\s", "").trim(); if (input.drivingLicenseNumber() != null) row.drivingLicenseNumber = input.drivingLicenseNumber().replaceAll("[\\s-]", "").trim().toUpperCase(Locale.ROOT);
        return view(row);
    }

    private void validatePreferredLocations(String value) {
        if (value == null || value.isBlank()) return;
        for (String location : value.split(",", -1)) {
            String item = location.trim();
            if (!item.matches("(?i)[a-z][a-z .'-]{1,79}") && !item.matches("[0-9]{6}"))
                throw new IllegalArgumentException("Preferred locations must be city names or 6-digit pincodes separated by commas");
        }
    }

    private void validateAadhaar(String value) {
        if (value == null || value.isBlank()) return;
        if (!value.matches("[0-9]{4}\\s?[0-9]{4}\\s?[0-9]{4}"))
            throw new IllegalArgumentException("Aadhaar number must contain 12 digits");
    }

    private void validateDrivingLicense(String value) {
        if (value == null || value.isBlank()) return;
        if (!value.matches("(?i)[a-z]{2}\\s?-?\\d{2}\\s?-?\\d{4}\\s?-?\\d{7}"))
            throw new IllegalArgumentException("Driving licence number format is invalid");
    }

    DocumentView uploadDocument(Long id, String token, String documentType, MultipartFile file) {
        TechnicianApplication row = access(id, token);
        TechnicianApplicationDocument existing = documents.findByApplicationIdAndDocumentType(id, documentType).orElse(null);
        boolean replacingRejected = existing != null && "REJECTED".equals(existing.reviewStatus);
        if (!Set.of("DOCUMENTS_PENDING", "DOCUMENTS_REJECTED").contains(row.status) && !replacingRejected) requireEditable(row);
        validateDocument(documentType, file);
        try {
            String type = file.getContentType().toLowerCase(Locale.ROOT); String key = storage.store(extension(type), file.getInputStream());
            TechnicianApplicationDocument document = existing == null ? new TechnicianApplicationDocument() : existing;
            document.application = row; document.documentType = documentType; document.originalFilename = cleanName(file.getOriginalFilename());
            document.contentType = type; document.fileSize = file.getSize(); document.storageKey = key;
            document.reviewStatus = "PENDING"; document.reviewerUserId = null; document.reviewReason = null; document.reviewedAt = null;
            if (Set.of("DOCUMENTS_REJECTED", "DOCUMENTS_PENDING").contains(row.status) || replacingRejected) row.status = "DOCUMENTS_PENDING";
            return documentView(documents.saveAndFlush(document));
        } catch (IOException ex) { throw new IllegalStateException("Document could not be saved", ex); }
    }

    ApplicationView submit(Long id, String token) {
        TechnicianApplication row = access(id, token);
        if (row.otpVerifiedAt == null || !Set.of("OTP_VERIFIED", "DOCUMENTS_PENDING", "DOCUMENTS_REJECTED").contains(row.status)) throw new IllegalArgumentException("Verify your mobile number before submitting a draft application");
        if (row.fullName == null || row.experience == null || row.specialization == null)
            throw new IllegalArgumentException("Complete your professional details before submitting");
        row.status = "DOCUMENTS_PENDING"; return view(row);
    }

    ApplicationView read(Long id, String token) { return view(access(id, token)); }
    record Download(Resource resource, String contentType, String filename) {}
    Download downloadDocument(Long applicationId, Long documentId) {
        admin(); TechnicianApplicationDocument document=documents.findById(documentId).filter(d -> d.application.id.equals(applicationId)).orElseThrow(() -> new IllegalArgumentException("Document not found"));
        Resource resource = storage.load(document.storageKey);
        if (!resource.exists() || !resource.isReadable()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Document file is unavailable");
        return new Download(resource, document.contentType, document.originalFilename);
    }
    List<ApplicationView> adminList(int page, int size) { admin(); return applications.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 100))).map(this::view).getContent(); }
    ApplicationView adminStatus(Long id, String status, String reason) {
        admin(); TechnicianApplication row=application(id); if (row.otpVerifiedAt == null || Set.of("DRAFT", "OTP_VERIFIED").contains(row.status)) throw new IllegalArgumentException("Only verified, submitted applications can be reviewed"); String target=status.trim().toUpperCase(Locale.ROOT);
        if ("UNDER_REVIEW".equals(target) && !Set.of("DOCUMENTS_PENDING","SUBMITTED","REJECTED").contains(row.status)) throw new IllegalArgumentException("Application is not ready for document review");
        if ("APPROVED".equals(target)) finalApprove(row);
        else if ("REJECTED".equals(target) || "SUSPENDED".equals(target)) {
            if (reason==null || reason.isBlank()) throw new IllegalArgumentException("A rejection reason is required");
            boolean hasRejectedDocument = documents.findByApplicationIdOrderByCreatedAtAsc(id).stream().anyMatch(d -> "REJECTED".equals(d.reviewStatus));
            if (!hasRejectedDocument) throw new IllegalArgumentException("Reject at least one document before rejecting the application");
            row.status=target; row.reviewReason=reason.trim(); row.reviewedAt=LocalDateTime.now(); row.reviewedBy=identities.actor();
        }
        else if ("UNDER_REVIEW".equals(target)) {
            if (!Set.of("DOCUMENTS_PENDING", "SUBMITTED", "REJECTED").contains(row.status)) throw new IllegalArgumentException("Application cannot be reopened from its current state");
            row.reviewReason=null; row.reviewedAt=LocalDateTime.now(); row.reviewedBy=identities.actor();
            var required=documents.findByApplicationIdOrderByCreatedAtAsc(id).stream().filter(d -> REQUIRED_DOCUMENTS.contains(d.documentType)).toList();
            row.status = required.stream().map(d -> d.documentType).collect(java.util.stream.Collectors.toSet()).containsAll(REQUIRED_DOCUMENTS)
                    && required.stream().allMatch(d -> "VERIFIED".equals(d.reviewStatus)) ? "DOCUMENTS_VERIFIED" : "DOCUMENTS_PENDING";
        }
        else throw new IllegalArgumentException("Use the verification actions for this state");
        return view(row);
    }

    ApplicationView reviewDocument(Long applicationId, Long documentId, String status, String reason) {
        admin(); TechnicianApplication row=application(applicationId); TechnicianApplicationDocument document=documents.findById(documentId).filter(d -> d.application.id.equals(applicationId)).orElseThrow(() -> new IllegalArgumentException("Document not found"));
        String target=status.trim().toUpperCase(Locale.ROOT); if (!Set.of("PENDING","VERIFIED","REJECTED").contains(target)) throw new IllegalArgumentException("Invalid document review status");
        if ("REJECTED".equals(target) && (reason==null || reason.isBlank())) throw new IllegalArgumentException("Rejection reason is required");
        document.reviewStatus=target; document.reviewerUserId="PENDING".equals(target) ? null : identities.actor().getId(); document.reviewReason="PENDING".equals(target) ? null : reason; document.reviewedAt="PENDING".equals(target) ? null : LocalDateTime.now(); documents.save(document);
        if ("REJECTED".equals(target)) row.status="DOCUMENTS_REJECTED"; else if ("PENDING".equals(target)) row.status="DOCUMENTS_PENDING"; else { var required=documents.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream().filter(d -> REQUIRED_DOCUMENTS.contains(d.documentType)).toList(); if (required.stream().map(d -> d.documentType).collect(java.util.stream.Collectors.toSet()).containsAll(REQUIRED_DOCUMENTS) && required.stream().allMatch(d -> "VERIFIED".equals(d.reviewStatus))) row.status="DOCUMENTS_VERIFIED"; }
        audit.record("TECHNICIAN_DOCUMENT_"+target,"TECHNICIAN_APPLICATION_DOCUMENT",documentId,"Technician document review"); return view(row);
    }

    ApplicationView meeting(Long id, TechnicianApplicationController.Meeting input) {
        admin(); TechnicianApplication row=application(id); if (!"DOCUMENTS_VERIFIED".equals(row.status)) {
            var required=documents.findByApplicationIdOrderByCreatedAtAsc(id).stream().filter(d -> REQUIRED_DOCUMENTS.contains(d.documentType)).toList();
            if (required.stream().map(d -> d.documentType).collect(java.util.stream.Collectors.toSet()).containsAll(REQUIRED_DOCUMENTS) && required.stream().allMatch(d -> "VERIFIED".equals(d.reviewStatus))) row.status="DOCUMENTS_VERIFIED";
            else throw new IllegalArgumentException("Documents must be verified first");
        }
        String mode=input.mode().trim().toUpperCase(Locale.ROOT); if (!Set.of("NONE","GOOGLE_MEET","PHONE","PHYSICAL").contains(mode)) throw new IllegalArgumentException("Invalid meeting mode");
        if (!"NONE".equals(mode) && input.meetingAt() == null) throw new IllegalArgumentException("Meeting date and time are required");
        if ("GOOGLE_MEET".equals(mode) && (input.meetingUrl() == null || input.meetingUrl().isBlank())) throw new IllegalArgumentException("Google Meet URL is required");
        if ("PHONE".equals(mode) && (input.meetingPhone() == null || input.meetingPhone().isBlank())) throw new IllegalArgumentException("Meeting phone is required");
        if ("PHYSICAL".equals(mode) && (input.meetingLocation() == null || input.meetingLocation().isBlank())) throw new IllegalArgumentException("Meeting location is required");
        row.meetingRequired=!"NONE".equals(mode); row.meetingMode=mode; row.meetingAt=input.meetingAt(); row.meetingUrl=input.meetingUrl(); row.meetingPhone=input.meetingPhone(); row.meetingLocation=input.meetingLocation(); row.meetingLatitude=input.meetingLatitude(); row.meetingLongitude=input.meetingLongitude(); row.meetingMapUrl=input.meetingMapUrl(); row.meetingNotes=input.notes();
        row.status=row.meetingRequired ? "MEETING_SCHEDULED" : "DOCUMENTS_VERIFIED"; audit.record("TECHNICIAN_MEETING_UPDATE","TECHNICIAN_APPLICATION",id,"Technician meeting updated"); return view(row);
    }

    ApplicationView completeMeeting(Long id, String notes) { admin(); TechnicianApplication row=application(id); if (!"MEETING_SCHEDULED".equals(row.status) && !"MEETING_REQUIRED".equals(row.status)) throw new IllegalArgumentException("Meeting is not scheduled"); row.status="MEETING_COMPLETED"; row.meetingCompletedAt=LocalDateTime.now(); row.meetingCompletedByUserId=identities.actor().getId(); row.meetingNotes=notes; audit.record("TECHNICIAN_MEETING_COMPLETED","TECHNICIAN_APPLICATION",id,"Technician meeting completed"); return view(row); }

    private void finalApprove(TechnicianApplication row) { if (!"DOCUMENTS_VERIFIED".equals(row.status) && !"MEETING_COMPLETED".equals(row.status)) { var required=documents.findByApplicationIdOrderByCreatedAtAsc(row.id).stream().filter(d -> REQUIRED_DOCUMENTS.contains(d.documentType)).toList(); if (required.stream().map(d -> d.documentType).collect(java.util.stream.Collectors.toSet()).containsAll(REQUIRED_DOCUMENTS) && required.stream().allMatch(d -> "VERIFIED".equals(d.reviewStatus)) && !row.meetingRequired) row.status="DOCUMENTS_VERIFIED"; } if ("DOCUMENTS_VERIFIED".equals(row.status) && !row.meetingRequired) {} else if (!"MEETING_COMPLETED".equals(row.status)) throw new IllegalArgumentException("Required verification steps are incomplete"); row.status="APPROVED"; row.finalApprovedAt=LocalDateTime.now(); row.finalApprovedByUserId=identities.actor().getId(); row.reviewedAt=row.finalApprovedAt; row.reviewedBy=identities.actor(); User user=provision(row); emails.technicianRegistrationApproved(user.getId(), row.fullName); audit.record("TECHNICIAN_FINAL_APPROVED","TECHNICIAN_APPLICATION",row.id,"Technician application approved"); }
    private TechnicianApplication application(Long id) { return applications.lockById(id).orElseThrow(() -> new IllegalArgumentException("Application not found")); }

    private User provision(TechnicianApplication application) {
        if (users.findByEmail(application.email).isPresent()) throw new IllegalArgumentException("A user already exists for this email");
        User user = new User(); user.setEmail(application.email); user.setPhone(application.phone); user.setPasswordHash(application.passwordHash); user.setRole(Role.TECHNICIAN); users.saveAndFlush(user);
        TechnicianProfile profile = new TechnicianProfile(); profile.user = user; profile.specialization = application.specialization; profile.assignedArea = application.preferredLocations; profile.availabilityStatus = "AVAILABLE"; technicians.saveAndFlush(profile); return user;
    }

    private TechnicianApplication admin() { User actor = identities.actor(); if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN) throw new AccessDeniedException("Access denied"); return null; }
    private TechnicianApplication access(Long id, String token) { if (token == null || token.isBlank()) throw new AccessDeniedException("Application access token required"); return applications.lockById(id).filter(row -> row.tokenHash.equals(hash(token))).orElseThrow(() -> new AccessDeniedException("Application access denied")); }
    private void requireDevelopmentOtp() {
        if (environment.acceptsProfiles(Profiles.of("prod")) || !environment.acceptsProfiles(Profiles.of("dev", "test")))
            throw new IllegalArgumentException("OTP delivery unavailable");
    }
    private void requireDraft(TechnicianApplication row) {
        if (!"DRAFT".equals(row.status)) throw new IllegalArgumentException("Application is no longer awaiting verification");
    }
    private void requireEditable(TechnicianApplication row) {
        if (!Set.of("DRAFT", "OTP_VERIFIED").contains(row.status)) throw new IllegalArgumentException("Submitted applications cannot be edited");
    }
    private String newToken() { return UUID.randomUUID() + UUID.randomUUID().toString(); }
    private String normalizeEmail(String value) { return required(value, "Email is required").trim().toLowerCase(Locale.ROOT); }
    private String normalizePhone(String value) { return required(value, "Mobile number is required").replaceAll("[^0-9+]", ""); }
    private String required(String value, String message) { if (value == null || value.isBlank()) throw new IllegalArgumentException(message); return value.trim(); }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private ApplicationView view(TechnicianApplication row) { return new ApplicationView(row.id,row.fullName,row.email,row.phone,row.experience,row.specialization,row.liftBrands,row.certifications,row.highestQualification,row.handsOnExperience,row.preferredLocations,row.willingToWorkAtHeights,row.travelAvailability,row.additionalNotes,row.aadhaarNumber,row.drivingLicenseNumber,row.status,row.reviewReason,row.otpVerifiedAt,row.createdAt,row.updatedAt,row.meetingRequired,row.meetingMode,row.meetingAt,row.meetingUrl,row.meetingPhone,row.meetingLocation,row.meetingLatitude,row.meetingLongitude,row.meetingMapUrl,row.meetingNotes,row.meetingCompletedAt,documents.findByApplicationIdOrderByCreatedAtAsc(row.id).stream().map(this::documentView).toList()); }
    private DocumentView documentView(TechnicianApplicationDocument row) { return new DocumentView(row.id,row.documentType,row.originalFilename,row.contentType,row.fileSize,row.createdAt,row.reviewStatus,row.reviewReason,row.reviewedAt); }
    private void validateDocument(String type, MultipartFile file) {
        if (!DOCUMENT_TYPES.contains(type)) throw new IllegalArgumentException("Unsupported document type");
        if (file == null || file.isEmpty() || file.getSize() > MAX_DOCUMENT_BYTES) throw new IllegalArgumentException("Document must be smaller than 5 MB");
        String contentType = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPES.contains(contentType)) throw new IllegalArgumentException("Use JPG, PNG, or PDF");
        byte[] expected = switch (contentType) {
            case "image/png" -> new byte[] {(byte)137,80,78,71,13,10,26,10};
            case "image/jpeg" -> new byte[] {(byte)255,(byte)216,(byte)255};
            default -> "%PDF-".getBytes(StandardCharsets.US_ASCII);
        };
        try (var input = file.getInputStream()) {
            if (!Arrays.equals(expected, input.readNBytes(expected.length)))
                throw new IllegalArgumentException("Document content does not match its file type");
        } catch (IOException ex) { throw new IllegalArgumentException("Document could not be read"); }
    }
    private String extension(String type) { return switch (type) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; default -> ".pdf"; }; }
    private String cleanName(String value) { String name = value == null || value.isBlank() ? "document" : value.replaceAll("[\\\\/\\r\\n]", "_"); return name.length() > 255 ? name.substring(name.length() - 255) : name; }
    record Created(ApplicationView application, String applicationToken) {}
    record OtpView(Long applicationId, LocalDateTime expiresAt, boolean developmentOnly, String otp) {}
    record ApplicationView(Long id,String fullName,String email,String phone,String experience,String specialization,String liftBrands,String certifications,String highestQualification,String handsOnExperience,String preferredLocations,Boolean willingToWorkAtHeights,String travelAvailability,String additionalNotes,String aadhaarNumber,String drivingLicenseNumber,String status,String reviewReason,LocalDateTime otpVerifiedAt,LocalDateTime createdAt,LocalDateTime updatedAt,boolean meetingRequired,String meetingMode,LocalDateTime meetingAt,String meetingUrl,String meetingPhone,String meetingLocation,java.math.BigDecimal meetingLatitude,java.math.BigDecimal meetingLongitude,String meetingMapUrl,String meetingNotes,LocalDateTime meetingCompletedAt,List<DocumentView> documents) {}
    record DocumentView(Long id,String documentType,String originalFilename,String contentType,Long fileSize,LocalDateTime createdAt,String reviewStatus,String reviewReason,LocalDateTime reviewedAt) {}
}
