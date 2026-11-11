ALTER TABLE payment_refunds DROP CONSTRAINT ck_payment_refunds_status;
ALTER TABLE payment_refunds
    ADD CONSTRAINT ck_payment_refunds_status
    CHECK (status IN ('REQUESTED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'REJECTED'));
ALTER TABLE payment_refunds ADD COLUMN rejected_by_user_id BIGINT NULL;
ALTER TABLE payment_refunds ADD COLUMN rejected_at DATETIME(6) NULL;
ALTER TABLE payment_refunds ADD COLUMN rejection_reason VARCHAR(500) NULL;
ALTER TABLE payment_refunds
    ADD CONSTRAINT fk_payment_refunds_rejected_by
    FOREIGN KEY (rejected_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;
