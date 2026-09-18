package com.valor.auth;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.valor.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/settings")
class AdminSettingsController {
    private final AdminSettingsService settings;

    AdminSettingsController(AdminSettingsService settings) {
        this.settings = settings;
    }

    record SettingsRequest(@NotBlank @Size(max = 200) String companyName,
            @Email @Size(max = 254) String supportEmail,
            @Size(max = 20) String supportPhone,
            @NotBlank @Size(max = 64) String timezone,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotBlank @Size(max = 20) String dateFormat,
            @Min(15) @Max(480) int defaultVisitDurationMinutes,
            @Min(0) @Max(365) int maintenanceReminderDays,
            @Min(5) @Max(1440) int emergencyResponseTargetMinutes,
            boolean emailNotificationsEnabled,
            boolean smsNotificationsEnabled,
            boolean autoAssignRequestsEnabled) {
        @JsonAnySetter public void unknown(String key, JsonNode value) { throw new IllegalArgumentException("Unsupported setting"); }
    }

    record SettingsView(String companyName, String supportEmail, String supportPhone, String timezone,
            String currency, String dateFormat, int defaultVisitDurationMinutes, int maintenanceReminderDays,
            int emergencyResponseTargetMinutes, boolean emailNotificationsEnabled,
            boolean smsNotificationsEnabled, boolean autoAssignRequestsEnabled,
            LocalDateTime createdAt, LocalDateTime updatedAt) {}

    @Operation(operationId = "getAdminSettings")
    @GetMapping
    ApiResponse<SettingsView> get() {
        return ApiResponse.success("Settings", settings.get(), 200);
    }

    @Operation(operationId = "updateAdminSettings")
    @PutMapping
    ApiResponse<SettingsView> update(@Valid @RequestBody SettingsRequest input) {
        return ApiResponse.success("Settings updated", settings.update(input), 200);
    }
}

@Service
@Transactional
class AdminSettingsService {
    private static final Set<String> CURRENCIES = Set.of("INR", "USD", "AED");
    private static final Set<String> DATE_FORMATS = Set.of("DD MMM YYYY", "MM/DD/YYYY", "YYYY-MM-DD");

    private final EntityManager em;
    private final AssetIdentityAccess identities;
    private final AuditService audit;

    AdminSettingsService(EntityManager em, AssetIdentityAccess identities, AuditService audit) {
        this.em = em;
        this.identities = identities;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    AdminSettingsController.SettingsView get() {
        identities.requireAdmin();
        return view(current(false));
    }

    AdminSettingsController.SettingsView update(AdminSettingsController.SettingsRequest input) {
        identities.requireAdmin();
        validate(input);
        AdminSettings settings = current(true);
        String before = "currency=" + settings.currency + ",timezone=" + settings.timezone + ",autoAssign=" + settings.autoAssignRequestsEnabled;
        settings.companyName = clean(input.companyName());
        settings.supportEmail = clean(input.supportEmail());
        settings.supportPhone = clean(input.supportPhone());
        settings.timezone = input.timezone().trim();
        settings.currency = input.currency().trim().toUpperCase();
        settings.dateFormat = input.dateFormat().trim();
        settings.defaultVisitDurationMinutes = input.defaultVisitDurationMinutes();
        settings.maintenanceReminderDays = input.maintenanceReminderDays();
        settings.emergencyResponseTargetMinutes = input.emergencyResponseTargetMinutes();
        settings.emailNotificationsEnabled = input.emailNotificationsEnabled();
        settings.smsNotificationsEnabled = input.smsNotificationsEnabled();
        settings.autoAssignRequestsEnabled = input.autoAssignRequestsEnabled();
        em.flush();
        String after = "currency=" + settings.currency + ",timezone=" + settings.timezone + ",autoAssign=" + settings.autoAssignRequestsEnabled;
        audit.record("SETTINGS_UPDATE", "ADMIN_SETTINGS", 1, "Updated admin settings", before, after, "SUCCESS");
        return view(settings);
    }

    private AdminSettings current(boolean lock) {
        AdminSettings settings = em.find(AdminSettings.class, 1L, lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
        if (settings != null) return settings;
        settings = new AdminSettings();
        em.persist(settings);
        em.flush();
        return settings;
    }

    private static void validate(AdminSettingsController.SettingsRequest input) {
        try {
            ZoneId.of(input.timezone().trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unsupported timezone");
        }
        if (!CURRENCIES.contains(input.currency().trim().toUpperCase())) throw new IllegalArgumentException("Unsupported currency");
        if (!DATE_FORMATS.contains(input.dateFormat().trim())) throw new IllegalArgumentException("Unsupported date format");
    }

    private static AdminSettingsController.SettingsView view(AdminSettings settings) {
        return new AdminSettingsController.SettingsView(settings.companyName, settings.supportEmail,
                settings.supportPhone, settings.timezone, settings.currency, settings.dateFormat,
                settings.defaultVisitDurationMinutes, settings.maintenanceReminderDays,
                settings.emergencyResponseTargetMinutes, settings.emailNotificationsEnabled,
                settings.smsNotificationsEnabled, settings.autoAssignRequestsEnabled,
                settings.createdAt, settings.updatedAt);
    }

    private static String clean(String value) {
        String result = value == null ? null : value.trim();
        return result == null || result.isEmpty() ? null : result;
    }
}
