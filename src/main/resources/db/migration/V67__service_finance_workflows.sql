CREATE TABLE service_inspections (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, service_request_id BIGINT NOT NULL,
 technician_profile_id BIGINT NOT NULL, reported_problem VARCHAR(2000) NOT NULL,
 observed_problem VARCHAR(2000) NOT NULL, root_cause VARCHAR(2000) NOT NULL,
 severity VARCHAR(20) NOT NULL, safety_status VARCHAR(30) NOT NULL,
 recommended_repair VARCHAR(2000) NOT NULL, required_labour VARCHAR(1000) NOT NULL,required_spare_parts VARCHAR(2000),
 estimated_duration_minutes INT NOT NULL, technician_remarks VARCHAR(2000),
 status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED', created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 reviewed_at DATETIME(6), reviewed_by_user_id BIGINT, review_reason VARCHAR(1000),
 CONSTRAINT fk_si_request FOREIGN KEY(service_request_id) REFERENCES service_requests(id),
 CONSTRAINT fk_si_technician FOREIGN KEY(technician_profile_id) REFERENCES technician_profiles(id),
 CONSTRAINT fk_si_reviewer FOREIGN KEY(reviewed_by_user_id) REFERENCES users(id),
 CONSTRAINT ck_si_duration CHECK(estimated_duration_minutes >= 0),
 CONSTRAINT ck_si_severity CHECK(severity IN ('UNASSESSED','LOW','MEDIUM','HIGH','CRITICAL')),
 CONSTRAINT ck_si_safety CHECK(safety_status IN ('UNASSESSED','SAFE','RESTRICTED','UNSAFE','CLEARED')),
 CONSTRAINT ck_si_status CHECK(status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED'))
);
CREATE INDEX idx_si_request ON service_inspections(service_request_id,id);
CREATE TABLE service_inspection_evidence (
 inspection_id BIGINT NOT NULL, attachment_id BIGINT NOT NULL,
 PRIMARY KEY(inspection_id,attachment_id),
 CONSTRAINT fk_sie_inspection FOREIGN KEY(inspection_id) REFERENCES service_inspections(id),
 CONSTRAINT fk_sie_attachment FOREIGN KEY(attachment_id) REFERENCES service_request_attachments(id)
);
CREATE TABLE service_finance_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, service_request_id BIGINT NOT NULL,
 event_type VARCHAR(60) NOT NULL, actor_user_id BIGINT NOT NULL, reason VARCHAR(2000) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_sfe_request FOREIGN KEY(service_request_id) REFERENCES service_requests(id),
 CONSTRAINT fk_sfe_actor FOREIGN KEY(actor_user_id) REFERENCES users(id)
);
CREATE TABLE service_completion_terms (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,service_request_id BIGINT NOT NULL,version_number INT NOT NULL,
 warranty_details VARCHAR(2000) NOT NULL,handover_notes VARCHAR(2000) NOT NULL,
 reviewed_by_user_id BIGINT NOT NULL,created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT uk_sct_version UNIQUE(service_request_id,version_number),
 CONSTRAINT fk_sct_job FOREIGN KEY(service_request_id) REFERENCES service_requests(id),
 CONSTRAINT fk_sct_actor FOREIGN KEY(reviewed_by_user_id) REFERENCES users(id)
);
CREATE TABLE service_work_approvals (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, service_request_id BIGINT NOT NULL,
 technician_profile_id BIGINT NOT NULL, description VARCHAR(2000) NOT NULL,
 reason VARCHAR(2000) NOT NULL, status VARCHAR(30) NOT NULL DEFAULT 'PENDING_ADMIN',
 quotation_id BIGINT, review_reason VARCHAR(1000), created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_swa_request FOREIGN KEY(service_request_id) REFERENCES service_requests(id),
 CONSTRAINT fk_swa_technician FOREIGN KEY(technician_profile_id) REFERENCES technician_profiles(id),
 CONSTRAINT fk_swa_quote FOREIGN KEY(quotation_id) REFERENCES service_quotations(id),
 CONSTRAINT ck_swa_status CHECK(status IN ('PENDING_ADMIN','AWAITING_CUSTOMER','APPROVED','REJECTED'))
);
ALTER TABLE service_quotations ADD COLUMN version_number INT NOT NULL DEFAULT 1;
ALTER TABLE service_quotations ADD COLUMN supersedes_quotation_id BIGINT;
ALTER TABLE service_quotations ADD COLUMN revision_kind VARCHAR(20) NOT NULL DEFAULT 'ORIGINAL';
ALTER TABLE service_quotations ADD COLUMN release_status VARCHAR(20) NOT NULL DEFAULT 'RELEASED';
ALTER TABLE service_quotations ADD COLUMN revision_reason VARCHAR(1000);
ALTER TABLE service_quotations ADD COLUMN approved_terms_hash VARCHAR(64);
ALTER TABLE service_quotations ADD CONSTRAINT fk_sq_parent FOREIGN KEY(supersedes_quotation_id) REFERENCES service_quotations(id);
CREATE TABLE service_quotation_version_backfill (quotation_id BIGINT PRIMARY KEY,version_number INT NOT NULL);
INSERT INTO service_quotation_version_backfill(quotation_id,version_number)
 SELECT id,ROW_NUMBER() OVER(PARTITION BY service_request_id ORDER BY created_at,id) FROM service_quotations;
UPDATE service_quotations SET version_number=(SELECT v.version_number FROM service_quotation_version_backfill v WHERE v.quotation_id=service_quotations.id);
DROP TABLE service_quotation_version_backfill;
ALTER TABLE service_quotations ADD CONSTRAINT uk_sq_job_version UNIQUE(service_request_id,version_number);
CREATE TABLE service_payment_milestones (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, quotation_id BIGINT NOT NULL, service_request_id BIGINT NOT NULL,
 sequence_number INT NOT NULL, label VARCHAR(120) NOT NULL, due_condition VARCHAR(30) NOT NULL,
 due_date DATE, amount DECIMAL(12,2) NOT NULL, invoice_id BIGINT,
 CONSTRAINT uk_spm_quote_sequence UNIQUE(quotation_id,sequence_number),
 CONSTRAINT uk_spm_invoice UNIQUE(invoice_id),
 CONSTRAINT fk_spm_quote FOREIGN KEY(quotation_id) REFERENCES service_quotations(id),
 CONSTRAINT fk_spm_request FOREIGN KEY(service_request_id) REFERENCES service_requests(id),
 CONSTRAINT fk_spm_invoice FOREIGN KEY(invoice_id) REFERENCES invoices(id),
 CONSTRAINT ck_spm_amount CHECK(amount > 0),
 CONSTRAINT ck_spm_condition CHECK(due_condition IN ('ON_APPROVAL','BEFORE_REPAIR','ON_COMPLETION','SCHEDULED'))
);
CREATE TABLE service_finance_holds (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, service_request_id BIGINT NOT NULL,
 reason VARCHAR(1000) NOT NULL, resolved_at DATETIME(6), actor_user_id BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_sfh_request FOREIGN KEY(service_request_id) REFERENCES service_requests(id),
 CONSTRAINT fk_sfh_actor FOREIGN KEY(actor_user_id) REFERENCES users(id)
);
CREATE TABLE technician_finance_settings (
 id INT PRIMARY KEY, payout_schedule VARCHAR(20) NOT NULL, manual_payout_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 updated_by_user_id BIGINT NOT NULL, updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_tfs_actor FOREIGN KEY(updated_by_user_id) REFERENCES users(id),
 CONSTRAINT ck_tfs_schedule CHECK(payout_schedule IN ('WEEKLY','FORTNIGHTLY','MONTHLY','MANUAL'))
);
ALTER TABLE technician_commissions ADD COLUMN eligibility_reason VARCHAR(1000);
ALTER TABLE technician_commissions ADD CONSTRAINT uk_commission_eligible_work UNIQUE(technician_profile_id,service_request_id,eligible_work_key);
ALTER TABLE technician_payout_batches ADD COLUMN initiated_by_user_id BIGINT;
ALTER TABLE technician_payout_batches ADD COLUMN evidence_attachment_id BIGINT;
CREATE TABLE finance_evidence (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, payout_batch_id BIGINT, service_request_id BIGINT,
 uploaded_by_user_id BIGINT NOT NULL, original_filename VARCHAR(255) NOT NULL,
 content_type VARCHAR(100) NOT NULL, storage_key VARCHAR(500) NOT NULL UNIQUE, file_size BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_fe_batch FOREIGN KEY(payout_batch_id) REFERENCES technician_payout_batches(id),
 CONSTRAINT fk_fe_job FOREIGN KEY(service_request_id) REFERENCES service_requests(id),
 CONSTRAINT fk_fe_actor FOREIGN KEY(uploaded_by_user_id) REFERENCES users(id)
);
ALTER TABLE technician_payout_batches ADD CONSTRAINT fk_tpb_evidence FOREIGN KEY(evidence_attachment_id) REFERENCES finance_evidence(id);
ALTER TABLE technician_expense_claims ADD COLUMN idempotency_key VARCHAR(80);
ALTER TABLE technician_expense_claims ADD CONSTRAINT uk_tec_key UNIQUE(technician_profile_id,idempotency_key);
CREATE TABLE job_part_usage (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, part_line_id BIGINT NOT NULL,
 action VARCHAR(20) NOT NULL, quantity INT NOT NULL, reason VARCHAR(1000) NOT NULL,
 actor_user_id BIGINT NOT NULL, idempotency_key VARCHAR(80) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT uk_jpu_key UNIQUE(actor_user_id,idempotency_key),
 CONSTRAINT fk_jpu_line FOREIGN KEY(part_line_id) REFERENCES service_request_part_lines(id),
 CONSTRAINT fk_jpu_actor FOREIGN KEY(actor_user_id) REFERENCES users(id),
 CONSTRAINT ck_jpu_action CHECK(action IN ('RECEIVED','INSTALLED','RETURNED')),
 CONSTRAINT ck_jpu_quantity CHECK(quantity > 0)
);
CREATE TABLE supplier_purchases (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, part_item_id BIGINT NOT NULL, service_request_id BIGINT,
 supplier_name VARCHAR(180) NOT NULL, reference VARCHAR(120) NOT NULL UNIQUE,
 quantity INT NOT NULL, unit_cost DECIMAL(12,2) NOT NULL, actor_user_id BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_sp_part FOREIGN KEY(part_item_id) REFERENCES part_items(id),
 CONSTRAINT fk_sp_job FOREIGN KEY(service_request_id) REFERENCES service_requests(id),
 CONSTRAINT fk_sp_actor FOREIGN KEY(actor_user_id) REFERENCES users(id),
 CONSTRAINT ck_sp_quantity CHECK(quantity > 0), CONSTRAINT ck_sp_cost CHECK(unit_cost >= 0)
);
CREATE TABLE invoice_commission_bases (
 invoice_id BIGINT PRIMARY KEY, net_labour_amount DECIMAL(12,2) NOT NULL,
 reason VARCHAR(1000) NOT NULL, approved_by_user_id BIGINT NOT NULL,
 approved_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_icb_invoice FOREIGN KEY(invoice_id) REFERENCES invoices(id),
 CONSTRAINT fk_icb_actor FOREIGN KEY(approved_by_user_id) REFERENCES users(id),
 CONSTRAINT ck_icb_amount CHECK(net_labour_amount >= 0)
);
CREATE TABLE technician_payout_adjustments (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, batch_id BIGINT NOT NULL, adjustment_id BIGINT NOT NULL,
 amount DECIMAL(12,2) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 active_adjustment_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('ACTIVE','PAID') THEN adjustment_id ELSE NULL END),
 CONSTRAINT uk_tpa_active UNIQUE(active_adjustment_id),
 CONSTRAINT fk_tpa_batch FOREIGN KEY(batch_id) REFERENCES technician_payout_batches(id),
 CONSTRAINT fk_tpa_adjustment FOREIGN KEY(adjustment_id) REFERENCES commission_adjustments(id),
 CONSTRAINT ck_tpa_status CHECK(status IN ('ACTIVE','RELEASED','PAID'))
);
CREATE TABLE finance_notification_outbox (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,payout_batch_id BIGINT NOT NULL,user_id BIGINT NOT NULL,
 message VARCHAR(500) NOT NULL,status VARCHAR(16) NOT NULL DEFAULT 'PENDING',attempts INT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),sent_at DATETIME(6),
 CONSTRAINT uk_fno_payout_user UNIQUE(payout_batch_id,user_id),
 CONSTRAINT fk_fno_payout FOREIGN KEY(payout_batch_id) REFERENCES technician_payout_batches(id),
 CONSTRAINT fk_fno_user FOREIGN KEY(user_id) REFERENCES users(id)
);
