package com.valor.auth;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "technician_saved_locations")
public class TechnicianSavedLocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "technician_profile_id", nullable = false) TechnicianProfile technician;
    @Column(nullable = false, length = 120) String title;
    @Column(nullable = false, length = 6) String pincode;
    @Column(length = 120) String state;
    @Column(length = 120) String district;
    @Column(length = 500) String address;
    @Column(nullable = false, precision = 9, scale = 6) BigDecimal latitude;
    @Column(nullable = false, precision = 9, scale = 6) BigDecimal longitude;
    @Column(name = "is_default", nullable = false) boolean defaultLocation;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;
    @PrePersist void prePersist() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void preUpdate() { updatedAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public TechnicianProfile getTechnician() { return technician; }
    public void setTechnician(TechnicianProfile v) { technician = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { title = clean(v, 120); }
    public String getPincode() { return pincode; }
    public void setPincode(String v) { pincode = clean(v, 6); }
    public String getState() { return state; }
    public void setState(String v) { state = clean(v, 120); }
    public String getDistrict() { return district; }
    public void setDistrict(String v) { district = clean(v, 120); }
    public String getAddress() { return address; }
    public void setAddress(String v) { address = clean(v, 500); }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal v) { latitude = v; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal v) { longitude = v; }
    public boolean isDefaultLocation() { return defaultLocation; }
    public void setDefaultLocation(boolean v) { defaultLocation = v; }
    private static String clean(String v, int max) { if (v == null || v.isBlank()) return null; String s = v.trim(); return s.length() > max ? s.substring(0, max) : s; }
}
