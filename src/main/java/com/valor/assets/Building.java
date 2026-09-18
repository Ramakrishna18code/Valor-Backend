package com.valor.assets;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.valor.auth.CustomerProfile;

@Entity(name = "Building")
@Table(name = "buildings")
@Getter @Setter
public class Building extends AssetRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfile customer;
    @Column(name = "building_name", nullable = false, length = 200)
    private String buildingName;
    @Column(name = "building_type", nullable = true, length = 80)
    private String buildingType;
    @Column(name = "address", nullable = true, length = 500)
    private String address;
    @Column(name = "city", nullable = true, length = 100)
    private String city;
    @Column(name = "state", nullable = true, length = 100)
    private String state;
    @Column(name = "pincode", nullable = true, length = 20)
    private String pincode;
    @Column(name = "latitude", nullable = true, precision = 9, scale = 6)
    private BigDecimal latitude;
    @Column(name = "longitude", nullable = true, precision = 9, scale = 6)
    private BigDecimal longitude;
    @Column(name = "emergency_contact_name", nullable = true, length = 160)
    private String emergencyContactName;
    @Column(name = "emergency_contact_phone", nullable = true, length = 20)
    private String emergencyContactPhone;
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
