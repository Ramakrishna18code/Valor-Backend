ALTER TABLE installation_requests DROP CONSTRAINT ck_installation_status;
ALTER TABLE installation_requests ADD CONSTRAINT ck_installation_status CHECK (status IN ('DRAFT','SUBMITTED','ASSESSMENT_IN_PROGRESS','ASSESSMENT_SUBMITTED','REQUIREMENTS_REVIEW','PROPOSAL_READY','PROPOSAL_ACCEPTED','PROPOSAL_DECLINED','IN_PROGRESS','COMPLETED','CANCELLED'));
ALTER TABLE installation_requests ADD COLUMN admin_requirements TEXT NULL;
ALTER TABLE installation_requests ADD COLUMN requirements_reviewed_at DATETIME(6) NULL;
ALTER TABLE installation_requests ADD COLUMN requirements_reviewed_by_user_id BIGINT NULL;
ALTER TABLE installation_requests ADD COLUMN construction_started_at DATETIME(6) NULL;
ALTER TABLE installation_requests ADD COLUMN completed_at DATETIME(6) NULL;
ALTER TABLE installation_requests ADD CONSTRAINT fk_installation_requirements_reviewer FOREIGN KEY (requirements_reviewed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;

CREATE TABLE installation_proposals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    installation_request_id BIGINT NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    scope TEXT NULL,
    notes TEXT NULL,
    terms TEXT NULL,
    valid_until DATETIME(6) NULL,
    status VARCHAR(30) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    presented_at DATETIME(6) NULL,
    decided_at DATETIME(6) NULL,
    decision_notes VARCHAR(2000) NULL,
    CONSTRAINT fk_installation_proposal_request FOREIGN KEY (installation_request_id) REFERENCES installation_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_installation_proposal_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_installation_proposal_request_version UNIQUE (installation_request_id, version),
    CONSTRAINT ck_installation_proposal_status CHECK (status IN ('DRAFT','PRESENTED','ACCEPTED','DECLINED','EXPIRED')),
    CONSTRAINT ck_installation_proposal_amount CHECK (amount >= 0)
);
CREATE INDEX idx_installation_proposal_request_status ON installation_proposals(installation_request_id, status, created_at);
