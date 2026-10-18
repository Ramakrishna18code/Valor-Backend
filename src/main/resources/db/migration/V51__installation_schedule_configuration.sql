CREATE TABLE installation_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    installation_request_id BIGINT NOT NULL,
    schedule_key VARCHAR(80) NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    currency VARCHAR(3) NULL,
    milestone_definitions TEXT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    approved_at DATETIME(6) NULL,
    superseded_at DATETIME(6) NULL,
    CONSTRAINT fk_installation_schedule_request FOREIGN KEY (installation_request_id) REFERENCES installation_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_installation_schedule_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_installation_schedule_version UNIQUE (schedule_key, version_no),
    CONSTRAINT ck_installation_schedule_status CHECK (status IN ('DRAFT','AWAITING_APPROVAL','APPROVED','SUPERSEDED','CANCELLED')),
    CONSTRAINT ck_installation_schedule_version CHECK (version_no > 0)
);
CREATE INDEX idx_installation_schedule_request ON installation_schedules(installation_request_id, status, version_no);

CREATE TABLE installation_schedule_approvals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    approval_type VARCHAR(30) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    approved_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reason VARCHAR(500) NULL,
    CONSTRAINT fk_installation_schedule_approval_schedule FOREIGN KEY (schedule_id) REFERENCES installation_schedules(id) ON DELETE RESTRICT,
    CONSTRAINT fk_installation_schedule_approval_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_installation_schedule_approval_once UNIQUE (schedule_id, approval_type),
    CONSTRAINT ck_installation_schedule_approval_type CHECK (approval_type IN ('COMPANY','CUSTOMER'))
);
CREATE INDEX idx_installation_schedule_approval_schedule ON installation_schedule_approvals(schedule_id, approval_type);
