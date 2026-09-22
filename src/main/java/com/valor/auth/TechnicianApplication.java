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
    @Column(nullable = false, length = 30) String status = "DRAFT";
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
