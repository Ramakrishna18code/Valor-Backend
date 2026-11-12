CREATE TABLE service_reminder_settings (
 id BIGINT PRIMARY KEY,
 medium_days INT NOT NULL DEFAULT 3,
 upcoming_days INT NOT NULL DEFAULT 7,
 scan_interval_minutes INT NOT NULL DEFAULT 15,
 report_email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT ck_reminder_medium_days CHECK (medium_days BETWEEN 1 AND 6),
 CONSTRAINT ck_reminder_upcoming_days CHECK (upcoming_days BETWEEN 4 AND 30),
 CONSTRAINT ck_reminder_scan_interval CHECK (scan_interval_minutes BETWEEN 1 AND 1440)
);
INSERT INTO service_reminder_settings(id) VALUES (1);

CREATE TABLE service_reminder_alerts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 recipient_user_id BIGINT NOT NULL,
 notification_id BIGINT NOT NULL,
 source_type VARCHAR(24) NOT NULL,
 source_id BIGINT NOT NULL,
 alert_kind VARCHAR(40) NOT NULL,
 due_date DATE NULL,
 event_key VARCHAR(180) NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'OPEN',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 resolved_at DATETIME(6) NULL,
 CONSTRAINT uk_service_reminder_alert_key UNIQUE(event_key),
 CONSTRAINT fk_reminder_recipient FOREIGN KEY(recipient_user_id) REFERENCES users(id),
 CONSTRAINT fk_reminder_notification FOREIGN KEY(notification_id) REFERENCES notifications(id),
 CONSTRAINT ck_reminder_alert_status CHECK(status IN ('OPEN','RESOLVED'))
);
CREATE INDEX idx_reminder_inbox ON service_reminder_alerts(recipient_user_id,status,created_at);
CREATE INDEX idx_reminder_source ON service_reminder_alerts(source_type,source_id,status);

CREATE TABLE daily_service_reminder_reports (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 report_date DATE NOT NULL,
 timezone VARCHAR(64) NOT NULL,
 total_pending BIGINT NOT NULL,
 today_scheduled BIGINT NOT NULL,
 overdue BIGINT NOT NULL,
 unassigned BIGINT NOT NULL,
 pending_acceptance BIGINT NOT NULL,
 in_progress BIGINT NOT NULL,
 upcoming_amc BIGINT NOT NULL,
 customer_approvals BIGINT NOT NULL,
 generated_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_daily_reminder_report UNIQUE(report_date,timezone)
);
