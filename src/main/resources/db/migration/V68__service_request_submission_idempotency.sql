ALTER TABLE service_requests
    ADD COLUMN submission_key VARCHAR(80) NULL;

CREATE UNIQUE INDEX uk_service_requests_customer_submission
    ON service_requests (customer_id, submission_key);
