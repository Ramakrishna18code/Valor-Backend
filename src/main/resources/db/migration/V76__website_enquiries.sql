CREATE TABLE website_enquiries (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 reference_code VARCHAR(36) NOT NULL,
 source VARCHAR(16) NOT NULL,
 name VARCHAR(120) NOT NULL,
 phone VARCHAR(24) NOT NULL,
 building VARCHAR(160) NULL,
 service_type VARCHAR(80) NULL,
 message VARCHAR(2000) NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'NEW',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT uk_website_enquiries_reference UNIQUE(reference_code),
 CONSTRAINT ck_website_enquiries_source CHECK(source IN ('SERVICE','CONTACT','EMERGENCY')),
 CONSTRAINT ck_website_enquiries_status CHECK(status IN ('NEW','CONTACTED','CLOSED'))
);
CREATE INDEX idx_website_enquiries_queue ON website_enquiries(status,created_at);
