package com.valor.installation;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class InstallationDtos {
    private InstallationDtos() {}
    public interface Strict { @JsonAnySetter default void unknown(String name, JsonNode value) { throw new IllegalArgumentException("Unsupported installation field"); } }
    public record AuthorizationInput(@Size(max=160) String ownerName, @Size(max=200) String organizationName, @Size(max=80) String relationshipType, @Size(max=120) String authorizationReference) implements Strict {}
    public record WriteRequest(@Positive Long buildingId, @Size(max=80) String propertyType, @Size(max=120) String intendedUse, @Size(max=40) String passengerOrGoods, @PositiveOrZero Integer estimatedFloorCount, @PositiveOrZero Integer estimatedCapacity, @Size(max=200) String preferredLocation, @Size(max=10000) String requirements, @Size(max=160) String siteContactName, @Size(max=30) String siteContactPhone, @Email @Size(max=254) String siteContactEmail, @Size(max=200) String siteAddressLine1, @Size(max=200) String siteAddressLine2, @Size(max=120) String siteLocality, @Size(max=120) String siteCity, @Size(max=120) String siteState, @Size(max=30) String sitePostalCode, @Size(max=120) String siteCountry, @Size(max=500) String sitePropertyDescription, @Size(max=100) String clientRequestKey, @PositiveOrZero Long version, AuthorizationInput authorization) implements Strict {}
    public record SubmitRequest(@NotBlank @Size(max=100) String submissionKey, @NotNull Long version) implements Strict {}
    public record CancelRequest(@NotBlank @Size(max=2000) String reason, @NotNull Long version) implements Strict {}
    public record AuthorizationReviewRequest(@NotNull AuthorizationStatus status, @Size(max=2000) String notes) implements Strict {}
    public record DocumentReference(@NotBlank @Size(max=80) String documentType, @NotBlank @Size(max=255) String originalFilename, @NotBlank @Size(max=100) String contentType, @NotNull @Positive Long fileSize, @NotBlank @Size(max=500) String storageKey) implements Strict {}
    public record DocumentView(Long id, String documentType, String originalFilename, String contentType, Long fileSize, LocalDateTime createdAt) {}
    public record HistoryView(Long id, InstallationStatus fromStatus, InstallationStatus toStatus, Long changedByUserId, String notes, LocalDateTime changedAt) {}
    public record AuthorizationView(Long id, String ownerName, String organizationName, String relationshipType, AuthorizationStatus status, String authorizationReference) {}
    public record View(Long id, Long customerProfileId, Long buildingId, AuthorizationView authorization, InstallationStatus status, String propertyType, String intendedUse, String passengerOrGoods, Integer estimatedFloorCount, Integer estimatedCapacity, String preferredLocation, String requirements, String siteContactName, String siteContactPhone, String siteContactEmail, String siteAddressLine1, String siteAddressLine2, String siteLocality, String siteCity, String siteState, String sitePostalCode, String siteCountry, String sitePropertyDescription, Long version, LocalDateTime submittedAt, LocalDateTime cancelledAt, LocalDateTime createdAt, LocalDateTime updatedAt, List<HistoryView> history, List<DocumentView> documents) {}
}
