package com.valor.protection;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

interface StrictProtectionInput {
    @JsonAnySetter default void rejectUnknown(String field, JsonNode value) {
        throw new IllegalArgumentException("Unsupported protection field: " + field);
    }
}

record ProviderWrite(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 1000) String description,
        @Pattern(regexp = "^https?://[^\\s]+$", message = "must be an http(s) URL") @Size(max = 500) String websiteUrl,
        @Email @Size(max = 254) String supportEmail,
        @Pattern(regexp = "^\\+?[0-9 ()-]{7,30}$") String supportPhone,
        boolean active) implements StrictProtectionInput {}

record ProductWrite(
        @NotNull @Positive Long providerId,
        @NotBlank @Size(max = 160) String name,
        @NotNull InsuredAssetType insuredAssetType,
        @NotBlank @Size(max = 2000) String coverageSummary,
        @Size(max = 2000) String eligibility,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal minimumPremium,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal maximumPremium,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        boolean active) implements StrictProtectionInput {}

record TechnicianPolicyWrite(
        @NotNull @Positive Long providerId,
        @Positive Long productId,
        @NotBlank @Size(max = 120) String insurerPolicyNumber,
        @NotNull LocalDate coverageStartDate,
        @NotNull LocalDate coverageEndDate,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal premiumAmount,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Size(max = 1000) String notes) implements StrictProtectionInput {}

record LiftPolicyWrite(
        @NotNull @Positive Long liftId,
        @NotNull @Positive Long providerId,
        @Positive Long productId,
        @NotBlank @Size(max = 120) String insurerPolicyNumber,
        @NotNull LocalDate coverageStartDate,
        @NotNull LocalDate coverageEndDate,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal premiumAmount,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal insuredValue,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Size(max = 1000) String notes) implements StrictProtectionInput {}

record PolicyReviewWrite(
        @NotNull PolicyStatus status,
        @NotBlank @Size(max = 160) String insurerReference,
        @NotBlank @Size(max = 1000) String insurerReviewNote) implements StrictProtectionInput {}

record LiftApplicationWrite(
        @NotNull @Positive Long liftId,
        @NotNull @Positive Long providerId,
        @Positive Long productId,
        @NotBlank @Size(max = 2000) String coverageRequested,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal insuredValue) implements StrictProtectionInput {}

record QuoteWrite(
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal quoteAmount,
        @Pattern(regexp = "^[A-Z]{3}$") String quoteCurrency,
        @NotNull LocalDate quoteExpiresOn,
        @Size(max = 160) String insurerQuoteReference,
        @NotBlank @Size(max = 1000) String insurerNotes) implements StrictProtectionInput {}

record ApplicationDecisionWrite(@NotBlank @Pattern(regexp = "CUSTOMER_ACCEPTED|CUSTOMER_DECLINED") String status)
        implements StrictProtectionInput {}

record ClaimWrite(
        @NotNull InsuredAssetType policyType,
        @NotNull @Positive Long policyId,
        @NotNull LocalDate incidentDate,
        @NotBlank @Size(max = 4000) String description,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal claimedAmount,
        @Pattern(regexp = "^[A-Z]{3}$") String currency) implements StrictProtectionInput {}

record InsurerClaimUpdate(
        @NotNull ClaimStatus status,
        @Size(max = 160) String insurerClaimReference,
        @Size(max = 1000) String insurerResponseNote) implements StrictProtectionInput {}

record RenewalWrite(
        @NotNull InsuredAssetType policyType,
        @NotNull @Positive Long policyId,
        LocalDate requestedStartDate,
        @Size(max = 1000) String note) implements StrictProtectionInput {}

record InsurerRenewalUpdate(
        @NotNull RenewalStatus status,
        @Size(max = 160) String insurerReference,
        @Size(max = 1000) String insurerResponseNote) implements StrictProtectionInput {}

record DocumentMetadataWrite(
        @NotBlank @Size(max = 80) String documentType,
        @NotBlank @Size(max = 255) String originalFilename,
        @NotBlank @Size(max = 120) String contentType,
        @Positive @Max(52428800) long fileSize,
        @NotBlank @Size(max = 500) @Pattern(regexp = "^[A-Za-z0-9_-]+(?:/[A-Za-z0-9._-]+)*$") String storageKey)
        implements StrictProtectionInput {}

record ProtectionPage<T>(List<T> items, int page, int size, long totalItems, int totalPages, boolean hasNext) {}
record ProviderView(Long id, String name, String description, String websiteUrl, String supportEmail, String supportPhone, boolean active) {}
record ProductView(Long id, Long providerId, String providerName, String name, InsuredAssetType insuredAssetType,
                   String coverageSummary, String eligibility, BigDecimal minimumPremium, BigDecimal maximumPremium,
                   String currency, boolean active) {}
record TechnicianPolicyView(Long id, Long technicianProfileId, Long providerId, String providerName, Long productId,
                            String productName, String insurerPolicyNumber, LocalDate coverageStartDate, LocalDate coverageEndDate,
                            BigDecimal premiumAmount, String currency, PolicyStatus status, String insurerReference,
                            String insurerReviewNote, String notes, LocalDateTime createdAt) {}
record LiftPolicyView(Long id, Long liftId, String liftName, Long providerId, String providerName, Long productId,
                      String productName, String insurerPolicyNumber, LocalDate coverageStartDate, LocalDate coverageEndDate,
                      BigDecimal premiumAmount, BigDecimal insuredValue, String currency, PolicyStatus status,
                      String insurerReference, String insurerReviewNote, String notes, LocalDateTime createdAt) {}
record LiftApplicationView(Long id, Long liftId, String liftName, Long providerId, String providerName, Long productId,
                           String productName, ApplicationStatus status, String coverageRequested, BigDecimal insuredValue,
                           BigDecimal quoteAmount, String quoteCurrency, LocalDate quoteExpiresOn, String insurerQuoteReference,
                           String insurerNotes, LocalDateTime submittedAt) {}
record ClaimView(Long id, String claimReference, InsuredAssetType policyType, Long policyId, String policyNumber,
                 String providerName, ClaimStatus status, LocalDate incidentDate, String description, BigDecimal claimedAmount,
                 String currency, String insurerClaimReference, String insurerResponseNote, LocalDateTime createdAt,
                 LocalDateTime updatedAt) {}
record RenewalView(Long id, InsuredAssetType policyType, Long policyId, String policyNumber, String providerName,
                   RenewalStatus status, LocalDate requestedStartDate, String note, String insurerReference,
                   String insurerResponseNote, LocalDateTime createdAt, LocalDateTime updatedAt) {}
record DocumentView(Long id, String documentType, String originalFilename, String contentType, long fileSize,
                    String storageKey, Long uploadedByUserId, LocalDateTime createdAt) {}
record ExpiryNoticeView(Long id, InsuredAssetType policyType, Long policyId, LocalDate expiryDate, int daysBeforeExpiry,
                        String message, LocalDateTime readAt, LocalDateTime createdAt) {}
record ActivityView(Long id, String activityType, String entityType, Long entityId, String summary, LocalDateTime createdAt) {}
record ProtectionDashboard(long technicianPolicies, long liftPolicies, long pendingApplications, long openClaims,
                           long openRenewals, long expiringPolicies, long unreadExpiryNotices,
                           List<ActivityView> recentActivity) {}
