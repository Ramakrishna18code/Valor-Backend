package com.valor.installation;

import com.valor.assets.AssetRecord;
import com.valor.assets.Building;
import com.valor.auth.CustomerProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "installation_requests", uniqueConstraints = {
        @UniqueConstraint(name = "uk_installation_client_key", columnNames = {"customer_id", "client_request_key"}),
        @UniqueConstraint(name = "uk_installation_submission_key", columnNames = {"customer_id", "submission_key"})
})
@Getter @Setter
public class InstallationRequest extends AssetRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfile customer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "building_id") private Building building;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "authorization_id") private PropertyAuthorization authorization;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private InstallationStatus status = InstallationStatus.DRAFT;
    @Column(name = "property_type", length = 80) private String propertyType;
    @Column(name = "intended_use", length = 120) private String intendedUse;
    @Column(name = "passenger_or_goods", length = 40) private String passengerOrGoods;
    @Column(name = "estimated_floor_count") private Integer estimatedFloorCount;
    @Column(name = "estimated_capacity") private Integer estimatedCapacity;
    @Column(name = "preferred_location", length = 200) private String preferredLocation;
    @Column(columnDefinition = "TEXT") private String requirements;
    @Column(name = "site_contact_name", length = 160) private String siteContactName;
    @Column(name = "site_contact_phone", length = 30) private String siteContactPhone;
    @Column(name = "site_contact_email", length = 254) private String siteContactEmail;
    @Column(name = "site_address_line1", length = 200) private String siteAddressLine1;
    @Column(name = "site_address_line2", length = 200) private String siteAddressLine2;
    @Column(name = "site_locality", length = 120) private String siteLocality;
    @Column(name = "site_city", length = 120) private String siteCity;
    @Column(name = "site_state", length = 120) private String siteState;
    @Column(name = "site_postal_code", length = 30) private String sitePostalCode;
    @Column(name = "site_country", length = 120) private String siteCountry;
    @Column(name = "site_property_description", length = 500) private String sitePropertyDescription;
    @Column(name = "client_request_key", length = 100) private String clientRequestKey;
    @Column(name = "submission_key", length = 100) private String submissionKey;
    @Version @Column(nullable = false) private Long version;
    @Column(name = "submitted_at") private java.time.LocalDateTime submittedAt;
    @Column(name = "cancelled_at") private java.time.LocalDateTime cancelledAt;
    @Column(name = "admin_requirements", columnDefinition = "TEXT") private String adminRequirements;
    @Column(name = "requirements_reviewed_at") private java.time.LocalDateTime requirementsReviewedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "requirements_reviewed_by_user_id") private com.valor.auth.User requirementsReviewedBy;
    @Column(name = "construction_started_at") private java.time.LocalDateTime constructionStartedAt;
    @Column(name = "completed_at") private java.time.LocalDateTime completedAt;
}
