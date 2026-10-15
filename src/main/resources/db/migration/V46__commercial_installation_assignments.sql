CREATE TABLE installation_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    installation_request_id BIGINT NOT NULL,
    technician_profile_id BIGINT NOT NULL,
    assigned_by_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    responded_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    notes VARCHAR(2000) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_install_assignment_request FOREIGN KEY (installation_request_id) REFERENCES installation_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_install_assignment_technician FOREIGN KEY (technician_profile_id) REFERENCES technician_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_install_assignment_admin FOREIGN KEY (assigned_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_install_assignment_status CHECK (status IN ('ASSIGNED','ACCEPTED','DECLINED','CANCELLED'))
);
CREATE INDEX idx_install_assignment_technician_status ON installation_assignments(technician_profile_id, status, assigned_at);
CREATE INDEX idx_install_assignment_request_status ON installation_assignments(installation_request_id, status, assigned_at);

CREATE TABLE installation_assignment_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id BIGINT NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NOT NULL,
    notes VARCHAR(2000) NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_install_assignment_history_assignment FOREIGN KEY (assignment_id) REFERENCES installation_assignments(id) ON DELETE CASCADE,
    CONSTRAINT fk_install_assignment_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
);
