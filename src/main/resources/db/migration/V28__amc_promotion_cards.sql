CREATE TABLE amc_promotion_cards (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    slot_number INT NOT NULL,
    quote VARCHAR(80) NOT NULL,
    title VARCHAR(120) NOT NULL,
    supporting_text VARCHAR(240) NOT NULL,
    cta_label VARCHAR(60) NOT NULL,
    image_url VARCHAR(500) NULL,
    background_color VARCHAR(20) NOT NULL DEFAULT '#EAF5FF',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_amc_promo_slot UNIQUE (slot_number),
    CONSTRAINT ck_amc_promo_slot CHECK (slot_number BETWEEN 1 AND 4)
);

INSERT INTO amc_promotion_cards
(slot_number, quote, title, supporting_text, cta_label, image_url, background_color, active)
VALUES
(1, '12+1 Months', 'AMC Service Offer', 'Get 1 Month FREE with 12 Months AMC', 'Renew Now', NULL, '#E9F7EF', TRUE),
(2, 'Priority Care', 'Breakdown Cover', 'Keep your lift protected with faster technician visits', 'Explore AMC', NULL, '#EEF4FF', TRUE),
(3, 'Smart Savings', 'Annual Lift Care', 'Bundle maintenance, inspection and support in one plan', 'View Plans', NULL, '#FFF4E8', TRUE),
(4, 'Renew Early', 'No Gap Coverage', 'Renew before expiry and avoid service interruptions', 'Request Renewal', NULL, '#F4EDFF', TRUE);
