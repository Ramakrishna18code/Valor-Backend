ALTER TABLE finance_evidence ADD COLUMN helper_allowance_id BIGINT NULL;
ALTER TABLE finance_evidence ADD CONSTRAINT fk_finance_evidence_helper_allowance FOREIGN KEY(helper_allowance_id) REFERENCES service_helper_allowances(id);
ALTER TABLE service_helper_allowances ADD COLUMN evidence_attachment_id BIGINT NULL;
ALTER TABLE service_helper_allowances ADD CONSTRAINT fk_helper_allowance_payment_evidence FOREIGN KEY(evidence_attachment_id) REFERENCES finance_evidence(id);
