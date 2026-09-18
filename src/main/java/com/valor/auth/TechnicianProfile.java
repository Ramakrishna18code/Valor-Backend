package com.valor.auth;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "technician_profiles")
public class TechnicianProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @OneToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false, unique = true) User user;
    @Column(name = "employee_id", length = 50, unique = true) String employeeId;
    @Column(name = "assigned_area", length = 160) String assignedArea;
    @Column(length = 160) String specialization;
    @Column(name = "availability_status", nullable = false, length = 20) String availabilityStatus = "AVAILABLE";
    @Column(precision = 3, scale = 2) java.math.BigDecimal rating;
    @Column(name = "last_working_day") LocalDate lastWorkingDay;
    @Column(name = "last_active_at") LocalDateTime lastActiveAt;
    @Column(name = "profile_photo_url", length = 500) String profilePhotoUrl;
    @Column(name = "date_of_birth") LocalDate dateOfBirth;
    @Column(length = 40) String gender;
    @Column(length = 500) String address;
    @Column(name = "emergency_contact_name", length = 160) String emergencyContactName;
    @Column(name = "emergency_contact_phone", length = 20) String emergencyContactPhone;
    @Column(name = "is_active", nullable = false) boolean active = true;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;

    @PrePersist void p() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void u() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public boolean isActive() { return active; }
    public String getEmployeeId() { return employeeId; }
    public String getAssignedArea() { return assignedArea; }
    public String getSpecialization() { return specialization; }
    public String getAvailabilityStatus() { return availabilityStatus; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public String getProfilePhotoUrl() { return profilePhotoUrl; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getGender() { return gender; }
    public String getAddress() { return address; }
    public String getEmergencyContactName() { return emergencyContactName; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setAvailabilityStatus(String value) { availabilityStatus = value; }
    public void setLastActiveAt(LocalDateTime value) { lastActiveAt = value; }
    public void setProfilePhotoUrl(String value) { profilePhotoUrl = clean(value, 500); }
    public void setDateOfBirth(LocalDate value) { dateOfBirth = value; }
    public void setGender(String value) { gender = clean(value, 40); }
    public void setAddress(String value) { address = clean(value, 500); }
    public void setEmergencyContactName(String value) { emergencyContactName = clean(value, 160); }
    public void setEmergencyContactPhone(String value) { emergencyContactPhone = clean(value, 20); }
    public void setAssignedArea(String value) { assignedArea = clean(value, 160); }
    public void setSpecialization(String value) { specialization = clean(value, 160); }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
