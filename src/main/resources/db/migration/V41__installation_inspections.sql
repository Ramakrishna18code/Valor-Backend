CREATE TABLE installation_inspections (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    installation_request_id BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'REQUESTED',
    scheduled_at DATETIME(6) NULL,
    scheduled_timezone VARCHAR(80) NULL,
    assigned_technician_id BIGINT NULL,
    visit_started_at DATETIME(6) NULL,
    submitted_at DATETIME(6) NULL,
    conclusion VARCHAR(40) NULL,
    measurements TEXT NULL,
    findings TEXT NULL,
    site_constraints TEXT NULL,
    readiness_observations TEXT NULL,
    issues TEXT NULL,
    risks TEXT NULL,
    recommended_actions TEXT NULL,
    reviewer_user_id BIGINT NULL,
    review_notes TEXT NULL,
    reviewed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_installation_inspection_request UNIQUE (installation_request_id),
    CONSTRAINT fk_inspection_request FOREIGN KEY (installation_request_id) REFERENCES installation_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_inspection_technician FOREIGN KEY (assigned_technician_id) REFERENCES technician_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_inspection_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_inspection_status CHECK (status IN ('REQUESTED','SCHEDULED','ASSIGNED','IN_PROGRESS','SUBMITTED','REVIEW_REQUIRED','APPROVED','REWORK_REQUIRED','CANCELLED','RESCHEDULE_REQUIRED'))
);
CREATE INDEX idx_inspection_status_schedule ON installation_inspections(status, scheduled_at, id);
CREATE TABLE installation_inspection_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inspection_id BIGINT NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    from_status VARCHAR(40) NULL,
    to_status VARCHAR(40) NOT NULL,
    notes VARCHAR(2000) NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_inspection_history_inspection FOREIGN KEY (inspection_id) REFERENCES installation_inspections(id) ON DELETE RESTRICT,
    CONSTRAINT fk_inspection_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
);
CREATE INDEX idx_inspection_history_changed ON installation_inspection_history(inspection_id, changed_at, id);
CREATE TABLE installation_inspection_evidence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inspection_id BIGINT NOT NULL,
    referenced_by_user_id BIGINT NOT NULL,
    document_type VARCHAR(80) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_inspection_evidence_inspection FOREIGN KEY (inspection_id) REFERENCES installation_inspections(id) ON DELETE RESTRICT,
    CONSTRAINT fk_inspection_evidence_user FOREIGN KEY (referenced_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_inspection_evidence_storage UNIQUE (storage_key)
);
CREATE INDEX idx_inspection_evidence_inspection ON installation_inspection_evidence(inspection_id, created_at, id);
