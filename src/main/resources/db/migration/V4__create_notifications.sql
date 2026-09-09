-- Stage 4: retained in-app notification records. External delivery is not implemented.
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scheduled_at DATETIME(6) NULL,
    sent_at DATETIME(6) NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_notifications_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH')),
    CONSTRAINT ck_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'READ'))
);
CREATE INDEX idx_notifications_recipient_state ON notifications(recipient_user_id, status, created_at);
CREATE INDEX idx_notifications_scheduled ON notifications(status, scheduled_at);
