CREATE TABLE service_request_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_request_id BIGINT NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_request_attachments_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_request_attachments_user FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_request_attachments_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_request_attachments_size CHECK (file_size > 0)
);

CREATE TABLE service_request_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_request_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_request_feedback_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_request_feedback_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT uk_request_feedback_request UNIQUE (service_request_id),
    CONSTRAINT ck_request_feedback_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX idx_request_attachments_request ON service_request_attachments(service_request_id, created_at);
CREATE INDEX idx_request_feedback_customer ON service_request_feedback(customer_id, updated_at);
