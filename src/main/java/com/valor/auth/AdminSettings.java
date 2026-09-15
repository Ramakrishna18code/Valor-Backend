package com.valor.auth;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_settings")
class AdminSettings {
    @Id
    Long id = 1L;

    @Column(nullable = false, length = 200)
    String companyName = "Valor Lift Services";

    @Column(length = 254)
    String supportEmail;

    @Column(length = 20)
    String supportPhone;

    @Column(nullable = false, length = 64)
    String timezone = "Asia/Kolkata";

    @Column(nullable = false, length = 3)
    String currency = "INR";

    @Column(nullable = false, length = 20)
    String dateFormat = "DD MMM YYYY";

    @Column(nullable = false)
    int defaultVisitDurationMinutes = 60;

    @Column(nullable = false)
    int maintenanceReminderDays = 30;

    @Column(nullable = false)
    int emergencyResponseTargetMinutes = 60;

    @Column(nullable = false)
    boolean emailNotificationsEnabled = false;

    @Column(nullable = false)
    boolean smsNotificationsEnabled = false;

    @Column(nullable = false)
    boolean autoAssignRequestsEnabled = false;

    @Column(nullable = false, updatable = false)
    LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
