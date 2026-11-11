CREATE TABLE service_quotation_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quotation_id BIGINT NOT NULL,
    line_number INT NOT NULL,
    pricing_code VARCHAR(80) NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity INT NOT NULL,
    unit_amount DECIMAL(12,2) NOT NULL,
    line_amount DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_service_quotation_items_quotation FOREIGN KEY (quotation_id) REFERENCES service_quotations(id) ON DELETE RESTRICT,
    CONSTRAINT uk_service_quotation_items_line UNIQUE (quotation_id, line_number),
    CONSTRAINT ck_service_quotation_items_amounts CHECK (quantity > 0 AND unit_amount > 0 AND line_amount = unit_amount * quantity)
);

CREATE TABLE invoice_line_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    line_number INT NOT NULL,
    pricing_code VARCHAR(80) NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity INT NOT NULL,
    unit_amount DECIMAL(12,2) NOT NULL,
    line_amount DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_invoice_line_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE RESTRICT,
    CONSTRAINT uk_invoice_line_items_line UNIQUE (invoice_id, line_number),
    CONSTRAINT ck_invoice_line_items_amounts CHECK (quantity > 0 AND unit_amount > 0 AND line_amount = unit_amount * quantity)
);
