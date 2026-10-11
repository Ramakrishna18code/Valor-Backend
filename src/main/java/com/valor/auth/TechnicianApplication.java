package com.valor.auth;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "technician_applications")
class TechnicianApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "application_token_hash", nullable = false, unique = true, length = 64) String tokenHash;
    @Column(name = "full_name", nullable = false, length = 160) String fullName;
    @Column(nullable = false, length = 254) String email;
    @Column(nullable = false, length = 20) String phone;
    @Column(name = "password_hash", nullable = false, length = 255) String passwordHash;
    @Column(length = 80) String experience;
    @Column(length = 160) String specialization;
    @Column(name = "lift_brands", length = 500) String liftBrands;
    @Column(length = 500) String certifications;
    @Column(name = "highest_qualification", length = 160) String highestQualification;
    @Column(name = "hands_on_experience", length = 160) String handsOnExperience;
    @Column(name = "preferred_locations", length = 500) String preferredLocations;
    @Column(name = "willing_to_work_at_heights") Boolean willingToWorkAtHeights;
    @Column(name = "travel_availability", length = 80) String travelAvailability;
    @Column(name = "additional_notes", length = 1000) String additionalNotes;
    @Column(name = "aadhaar_number", length = 20) String aadhaarNumber;
    @Column(name = "driving_license_number", length = 40) String drivingLicenseNumber;
    @Column(nullable = false, length = 30) String status = "DRAFT";
    @Column(name = "meeting_required", nullable = false) boolean meetingRequired;
    @Column(name = "meeting_mode", length = 20) String meetingMode;
    @Column(name = "meeting_at") LocalDateTime meetingAt;
    @Column(name = "meeting_url", length = 500) String meetingUrl;
    @Column(name = "meeting_phone", length = 40) String meetingPhone;
    @Column(name = "meeting_location", length = 500) String meetingLocation;
    @Column(name = "meeting_latitude", precision = 9, scale = 6) java.math.BigDecimal meetingLatitude;
    @Column(name = "meeting_longitude", precision = 9, scale = 6) java.math.BigDecimal meetingLongitude;
    @Column(name = "meeting_map_url", length = 500) String meetingMapUrl;
    @Column(name = "meeting_notes", length = 1000) String meetingNotes;
    @Column(name = "meeting_completed_at") LocalDateTime meetingCompletedAt;
    @Column(name = "meeting_completed_by_user_id") Long meetingCompletedByUserId;
    @Column(name = "final_approved_at") LocalDateTime finalApprovedAt;
    @Column(name = "final_approved_by_user_id") Long finalApprovedByUserId;
    @Column(name = "otp_hash", length = 255) String otpHash;
    @Column(name = "otp_expires_at") LocalDateTime otpExpiresAt;
    @Column(name = "otp_attempts_remaining", nullable = false) Integer otpAttemptsRemaining = 3;
    @Column(name = "otp_verified_at") LocalDateTime otpVerifiedAt;
    @Column(name = "reviewed_at") LocalDateTime reviewedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by_user_id") User reviewedBy;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
