-- Stage 3 service workflow; V1 and V2 remain unchanged.

CREATE TABLE service_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    lift_id BIGINT NOT NULL,
    service_id VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    issue_category VARCHAR(100) NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    service_type VARCHAR(40) NOT NULL,
    customer_remarks VARCHAR(2000) NULL,
    technician_remarks VARCHAR(2000) NULL,
    service_requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    preferred_visit_date DATE NULL,
    preferred_time_slot VARCHAR(80) NULL,
    internal_admin_notes VARCHAR(2000) NULL,
    completed_at DATETIME(6) NULL,
    estimated_completion_minutes INT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_service_requests_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_requests_lift FOREIGN KEY (lift_id) REFERENCES lifts(id) ON DELETE RESTRICT,
    CONSTRAINT uk_service_id UNIQUE (service_id),
    CONSTRAINT ck_service_requests_status CHECK (status IN ('PENDING', 'ASSIGNED', 'ACCEPTED', 'ON_THE_WAY', 'REACHED_SITE', 'DIAGNOSIS', 'REPAIR_IN_PROGRESS', 'WAITING_FOR_PARTS', 'TESTING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_service_requests_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'EMERGENCY')),
    CONSTRAINT ck_service_requests_service_type CHECK (service_type IN ('ROUTINE_MAINTENANCE', 'BREAKDOWN', 'EMERGENCY', 'INSPECTION', 'INSTALLATION', 'MODERNIZATION')),
    CONSTRAINT ck_requests_estimate CHECK (estimated_completion_minutes >= 0)
);

CREATE TABLE technician_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_request_id BIGINT NOT NULL,
    technician_id BIGINT NOT NULL,
    assigned_by_user_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ASSIGNED',
    assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    accepted_at DATETIME(6) NULL,
    released_at DATETIME(6) NULL,
    notes VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_technician_assignments_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_technician_assignments_technician FOREIGN KEY (technician_id) REFERENCES technician_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_technician_assignments_assignedby FOREIGN KEY (assigned_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_technician_assignments_status CHECK (status IN ('ASSIGNED', 'ACCEPTED', 'REJECTED', 'RELEASED', 'COMPLETED'))
);

ALTER TABLE technician_assignments
  ADD COLUMN active_request_id BIGINT GENERATED ALWAYS AS (
    CASE
      WHEN status IN ('ASSIGNED', 'ACCEPTED')
      THEN service_request_id
      ELSE NULL
    END
  ) STORED;

CREATE UNIQUE INDEX uk_assignment_active_request
  ON technician_assignments (active_request_id);

CREATE TABLE service_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_request_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    reported_by_user_id BIGINT NOT NULL,
    diagnosis TEXT NOT NULL,
    work_performed TEXT NOT NULL,
    testing_result TEXT NOT NULL,
    completion_notes TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_service_reports_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_reports_assignment FOREIGN KEY (assignment_id) REFERENCES technician_assignments(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_reports_reportedby FOREIGN KEY (reported_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_service_reports_request UNIQUE (service_request_id),
    CONSTRAINT ck_service_reports_diagnosis CHECK (CHAR_LENGTH(TRIM(diagnosis)) > 0),
    CONSTRAINT ck_service_reports_work_performed CHECK (CHAR_LENGTH(TRIM(work_performed)) > 0),
    CONSTRAINT ck_service_reports_testing_result CHECK (CHAR_LENGTH(TRIM(testing_result)) > 0)
);

CREATE TABLE service_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_request_id BIGINT NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    notes VARCHAR(2000) NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_service_status_history_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_status_history_changedby FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_service_status_history_from_status CHECK (from_status IN ('PENDING', 'ASSIGNED', 'ACCEPTED', 'ON_THE_WAY', 'REACHED_SITE', 'DIAGNOSIS', 'REPAIR_IN_PROGRESS', 'WAITING_FOR_PARTS', 'TESTING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_service_status_history_to_status CHECK (to_status IN ('PENDING', 'ASSIGNED', 'ACCEPTED', 'ON_THE_WAY', 'REACHED_SITE', 'DIAGNOSIS', 'REPAIR_IN_PROGRESS', 'WAITING_FOR_PARTS', 'TESTING', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_requests_customer_status_created ON service_requests(customer_id, status, created_at);
CREATE INDEX idx_requests_lift_status ON service_requests(lift_id, status);
CREATE INDEX idx_requests_status_priority ON service_requests(status, priority, service_requested_at);
CREATE INDEX idx_requests_visit_date ON service_requests(preferred_visit_date, preferred_time_slot);
CREATE INDEX idx_assignment_technician_status ON technician_assignments(technician_id, status, assigned_at);
CREATE INDEX idx_assignment_request_status ON technician_assignments(service_request_id, status);
CREATE INDEX idx_history_request_changed ON service_status_history(service_request_id, changed_at);
CREATE INDEX idx_history_actor_changed ON service_status_history(changed_by_user_id, changed_at);
CREATE INDEX idx_service_reports_assignment ON service_reports(assignment_id);
CREATE INDEX idx_service_reports_reporter ON service_reports(reported_by_user_id);
