-- Canonical typed service visits and technician change requests.
CREATE TABLE service_visits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_request_id BIGINT NOT NULL,
    technician_profile_id BIGINT NOT NULL,
    scheduled_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    notes VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    active_request_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('SCHEDULED', 'IN_PROGRESS') THEN service_request_id ELSE NULL END) STORED,
    CONSTRAINT fk_service_visits_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_visits_technician FOREIGN KEY (technician_profile_id) REFERENCES technician_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT ck_service_visits_status CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_service_visits_time CHECK (start_time < end_time)
);
CREATE UNIQUE INDEX uk_service_visits_active_request ON service_visits(active_request_id);
CREATE INDEX idx_service_visits_calendar ON service_visits(scheduled_date, status, start_time);
CREATE INDEX idx_service_visits_technician_time ON service_visits(technician_profile_id, scheduled_date, start_time, end_time, status);
CREATE INDEX idx_service_visits_request_status ON service_visits(service_request_id, status, scheduled_date);

CREATE TABLE visit_change_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_request_id BIGINT NOT NULL,
    service_visit_id BIGINT NULL,
    requested_by_user_id BIGINT NOT NULL,
    requested_technician_profile_id BIGINT NOT NULL,
    request_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(2000) NOT NULL,
    requested_date DATE NOT NULL,
    requested_start_time TIME NOT NULL,
    requested_end_time TIME NOT NULL,
    reviewed_by_user_id BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    review_notes VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_visit_change_request_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_change_request_visit FOREIGN KEY (service_visit_id) REFERENCES service_visits(id) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_change_request_actor FOREIGN KEY (requested_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_change_request_technician FOREIGN KEY (requested_technician_profile_id) REFERENCES technician_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_change_request_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_visit_change_request_type CHECK (request_type IN ('RESCHEDULE', 'ADDITIONAL_VISIT')),
    CONSTRAINT ck_visit_change_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_visit_change_request_time CHECK (requested_start_time < requested_end_time)
);
CREATE INDEX idx_visit_change_requests_status ON visit_change_requests(status, request_type, created_at);
CREATE INDEX idx_visit_change_requests_visit ON visit_change_requests(service_visit_id, status);

CREATE TABLE service_visit_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_visit_id BIGINT NOT NULL,
    service_request_id BIGINT NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NULL,
    from_technician_profile_id BIGINT NULL,
    to_technician_profile_id BIGINT NULL,
    scheduled_date DATE NULL,
    start_time TIME NULL,
    end_time TIME NULL,
    reason VARCHAR(2000) NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_service_visit_history_visit FOREIGN KEY (service_visit_id) REFERENCES service_visits(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_visit_history_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_visit_history_actor FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_visit_history_from_tech FOREIGN KEY (from_technician_profile_id) REFERENCES technician_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_visit_history_to_tech FOREIGN KEY (to_technician_profile_id) REFERENCES technician_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT ck_service_visit_history_from_status CHECK (from_status IS NULL OR from_status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_service_visit_history_to_status CHECK (to_status IS NULL OR to_status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);
CREATE INDEX idx_service_visit_history_visit_changed ON service_visit_history(service_visit_id, changed_at, id);
CREATE INDEX idx_service_visit_history_request_changed ON service_visit_history(service_request_id, changed_at, id);
