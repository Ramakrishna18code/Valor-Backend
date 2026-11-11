ALTER TABLE invoices ADD COLUMN issued_at DATETIME(6) NULL;
ALTER TABLE payment_records ADD COLUMN verified_at DATETIME(6) NULL;
