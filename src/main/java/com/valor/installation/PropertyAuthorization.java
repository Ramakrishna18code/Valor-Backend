package com.valor.installation;

import com.valor.assets.AssetRecord;
import com.valor.auth.CustomerProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "installation_property_authorizations")
@Getter @Setter
public class PropertyAuthorization extends AssetRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfile customer;
    @Column(name = "owner_name", length = 160) private String ownerName;
    @Column(name = "organization_name", length = 200) private String organizationName;
    @Column(name = "relationship_type", length = 80) private String relationshipType;
    @Enumerated(EnumType.STRING) @Column(name = "authorization_status", nullable = false, length = 30)
    private AuthorizationStatus status = AuthorizationStatus.PENDING_REVIEW;
    @Column(name = "authorization_reference", length = 120) private String authorizationReference;
}
