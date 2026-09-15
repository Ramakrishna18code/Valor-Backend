CREATE TABLE admin_settings (
    id BIGINT PRIMARY KEY,
    company_name VARCHAR(200) NOT NULL,
    support_email VARCHAR(254) NULL,
    support_phone VARCHAR(20) NULL,
    timezone VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    date_format VARCHAR(20) NOT NULL,
    default_visit_duration_minutes INT NOT NULL,
    maintenance_reminder_days INT NOT NULL,
    emergency_response_target_minutes INT NOT NULL,
    email_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    sms_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    auto_assign_requests_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

INSERT INTO admin_settings (
    id,
    company_name,
    timezone,
    currency,
    date_format,
    default_visit_duration_minutes,
    maintenance_reminder_days,
    emergency_response_target_minutes,
    email_notifications_enabled,
    sms_notifications_enabled,
    auto_assign_requests_enabled
) VALUES (
    1,
    'Valor Lift Services',
    'Asia/Kolkata',
    'INR',
    'DD MMM YYYY',
    60,
    30,
    60,
    FALSE,
    FALSE,
    FALSE
);
