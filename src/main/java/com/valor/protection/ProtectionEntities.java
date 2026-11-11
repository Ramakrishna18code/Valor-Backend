package com.valor.protection;

import com.valor.auth.CustomerProfile;
import com.valor.auth.TechnicianProfile;
import com.valor.auth.User;
import com.valor.assets.Lift;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "insurance_providers")
class InsuranceProvider {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, unique = true, length = 160) String name;
    @Column(length = 1000) String description;
    @Column(name = "website_url", length = 500) String websiteUrl;
    @Column(name = "support_email", length = 254) String supportEmail;
    @Column(name = "support_phone", length = 30) String supportPhone;
    @Column(nullable = false) boolean active = true;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_user_id", nullable = false) User createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;
    @PrePersist void created() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "insurance_products")
class InsuranceProduct {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "provider_id", nullable = false) InsuranceProvider provider;
    @Column(nullable = false, length = 160) String name;
    @Enumerated(EnumType.STRING) @Column(name = "insured_asset_type", nullable = false, length = 20) InsuredAssetType insuredAssetType;
    @Column(name = "coverage_summary", nullable = false, length = 2000) String coverageSummary;
    @Column(length = 2000) String eligibility;
    @Column(name = "minimum_premium", precision = 12, scale = 2) BigDecimal minimumPremium;
    @Column(name = "maximum_premium", precision = 12, scale = 2) BigDecimal maximumPremium;
    @Column(nullable = false, length = 3) String currency = "INR";
    @Column(nullable = false) boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;
    @PrePersist void created() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "technician_insurance_policies")
class TechnicianInsurancePolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "technician_profile_id", nullable = false) TechnicianProfile technician;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "provider_id", nullable = false) InsuranceProvider provider;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id") InsuranceProduct product;
    @Column(name = "insurer_policy_number", nullable = false, length = 120) String insurerPolicyNumber;
    @Column(name = "coverage_start_date", nullable = false) LocalDate coverageStartDate;
    @Column(name = "coverage_end_date", nullable = false) LocalDate coverageEndDate;
    @Column(name = "premium_amount", precision = 12, scale = 2) BigDecimal premiumAmount;
    @Column(nullable = false, length = 3) String currency = "INR";
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) PolicyStatus status = PolicyStatus.DECLARED;
    @Column(name = "insurer_reference", length = 160) String insurerReference;
    @Column(name = "insurer_review_note", length = 1000) String insurerReviewNote;
    @Column(length = 1000) String notes;
    @Column(name = "created_at", nullable = false, updatable = false) LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;
    @PrePersist void created() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "lift_insurance_policies")
class LiftInsurancePolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lift_id", nullable = false) Lift lift;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "provider_id", nullable = false) InsuranceProvider provider;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id") InsuranceProduct product;
    @Column(name = "insurer_policy_number", nullable = false, length = 120) String insurerPolicyNumber;
    @Column(name = "coverage_start_date", nullable = false) LocalDate coverageStartDate;
    @Column(name = "coverage_end_date", nullable = false) LocalDate coverageEndDate;
    @Column(name = "premium_amount", precision = 12, scale = 2) BigDecimal premiumAmount;
    @Column(name = "insured_value", precision = 14, scale = 2) BigDecimal insuredValue;
    @Column(nullable = false, length = 3) String currency = "INR";
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) PolicyStatus status = PolicyStatus.DECLARED;
    @Column(name = "insurer_reference", length = 160) String insurerReference;
    @Column(name = "insurer_review_note", length = 1000) String insurerReviewNote;
    @Column(length = 1000) String notes;
    @Column(name = "created_at", nullable = false, updatable = false) LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;
    @PrePersist void created() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "lift_insurance_applications")
class LiftInsuranceApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_profile_id", nullable = false) CustomerProfile customer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lift_id", nullable = false) Lift lift;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "provider_id", nullable = false) InsuranceProvider provider;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id") InsuranceProduct product;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) ApplicationStatus status = ApplicationStatus.SUBMITTED;
    @Column(name = "coverage_requested", nullable = false, length = 2000) String coverageRequested;
    @Column(name = "insured_value", precision = 14, scale = 2) BigDecimal insuredValue;
    @Column(name = "quote_amount", precision = 12, scale = 2) BigDecimal quoteAmount;
    @Column(name = "quote_currency", nullable = false, length = 3) String quoteCurrency = "INR";
    @Column(name = "quote_expires_on") LocalDate quoteExpiresOn;
    @Column(name = "insurer_quote_reference", length = 160) String insurerQuoteReference;
    @Column(name = "insurer_notes", length = 1000) String insurerNotes;
    @Column(name = "submitted_at", nullable = false, updatable = false) LocalDateTime submittedAt;
    @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;
    @PrePersist void created() { submittedAt = LocalDateTime.now(); updatedAt = submittedAt; }
    @PreUpdate void updated() { updatedAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "insurance_claims")
class InsuranceClaim {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "claim_reference", nullable = false, unique = true, length = 40) String claimReference;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "technician_policy_id") TechnicianInsurancePolicy technicianPolicy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "lift_policy_id") LiftInsurancePolicy liftPolicy;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "claimant_user_id", nullable = false) User claimant;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) ClaimStatus status = ClaimStatus.SUBMITTED;
    @Column(name = "incident_date", nullable = false) LocalDate incidentDate;
    @Column(nullable = false, length = 4000) String description;
    @Column(name = "claimed_amount", precision = 14, scale = 2) BigDecimal claimedAmount;
    @Column(nullable = false, length = 3) String currency = "INR";
    @Column(name = "insurer_claim_reference", length = 160) String insurerClaimReference;
    @Column(name = "insurer_response_note", length = 1000) String insurerResponseNote;
    @Column(name = "created_at", nullable = false, updatable = false) LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;
    @PrePersist void created() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "insurance_renewal_requests")
class InsuranceRenewalRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "technician_policy_id") TechnicianInsurancePolicy technicianPolicy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "lift_policy_id") LiftInsurancePolicy liftPolicy;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "requested_by_user_id", nullable = false) User requestedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) RenewalStatus status = RenewalStatus.REQUESTED;
    @Column(name = "requested_start_date") LocalDate requestedStartDate;
    @Column(length = 1000) String note;
    @Column(name = "insurer_reference", length = 160) String insurerReference;
    @Column(name = "insurer_response_note", length = 1000) String insurerResponseNote;
    @Column(name = "created_at", nullable = false, updatable = false) LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;
    @PrePersist void created() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "insurance_documents")
class InsuranceDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "claim_id") InsuranceClaim claim;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "lift_application_id") LiftInsuranceApplication application;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "technician_policy_id") TechnicianInsurancePolicy technicianPolicy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "lift_policy_id") LiftInsurancePolicy liftPolicy;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "uploaded_by_user_id", nullable = false) User uploadedBy;
    @Column(name = "document_type", nullable = false, length = 80) String documentType;
    @Column(name = "original_filename", nullable = false, length = 255) String originalFilename;
    @Column(name = "content_type", nullable = false, length = 120) String contentType;
    @Column(name = "file_size", nullable = false) long fileSize;
    @Column(name = "storage_key", nullable = false, unique = true, length = 500) String storageKey;
    @Column(name = "created_at", nullable = false, updatable = false) LocalDateTime createdAt;
    @PrePersist void created() { createdAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "insurance_expiry_notices")
class InsuranceExpiryNotice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recipient_user_id", nullable = false) User recipient;
    @Enumerated(EnumType.STRING) @Column(name = "policy_type", nullable = false, length = 20) InsuredAssetType policyType;
    @Column(name = "policy_id", nullable = false) Long policyId;
    @Column(name = "days_before_expiry", nullable = false) short daysBeforeExpiry;
    @Column(name = "expiry_date", nullable = false) LocalDate expiryDate;
    @Column(nullable = false, length = 500) String message;
    @Column(name = "read_at") LocalDateTime readAt;
    @Column(name = "created_at", nullable = false, updatable = false) LocalDateTime createdAt;
    @PrePersist void created() { createdAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "insurance_activity")
class InsuranceActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "visible_to_user_id") User visibleTo;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_user_id") User actor;
    @Column(name = "activity_type", nullable = false, length = 80) String activityType;
    @Column(name = "entity_type", nullable = false, length = 40) String entityType;
    @Column(name = "entity_id", nullable = false) Long entityId;
    @Column(nullable = false, length = 500) String summary;
    @Column(name = "created_at", nullable = false, updatable = false) LocalDateTime createdAt;
    @PrePersist void created() { createdAt = LocalDateTime.now(); }
}

enum InsuredAssetType { TECHNICIAN, LIFT }
enum PolicyStatus { DECLARED, INSURER_CONFIRMED_ACTIVE, INSURER_CONFIRMED_INACTIVE }
enum ApplicationStatus { SUBMITTED, QUOTE_RECEIVED, CUSTOMER_ACCEPTED, CUSTOMER_DECLINED, CANCELLED }
enum ClaimStatus { SUBMITTED, DOCUMENTS_REQUESTED, FORWARDED_TO_INSURER, INSURER_DECISION_PENDING, INSURER_APPROVED, INSURER_REJECTED, WITHDRAWN }
enum RenewalStatus { REQUESTED, UNDER_REVIEW, INSURER_CONFIRMED, INSURER_DECLINED, CANCELLED }
