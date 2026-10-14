CREATE TABLE verified_pincodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pincode VARCHAR(6) NOT NULL,
    state VARCHAR(120) NOT NULL,
    district VARCHAR(120) NOT NULL,
    post_office VARCHAR(160) NOT NULL,
    locality VARCHAR(160) NULL,
    source_name VARCHAR(160) NOT NULL,
    source_version VARCHAR(120) NOT NULL,
    source_updated_at DATE NULL,
    verified_at DATETIME(6) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_verified_pincode_post_office UNIQUE (pincode, post_office),
    CONSTRAINT ck_verified_pincode_format CHECK (pincode REGEXP '^[1-9][0-9]{5}$')
);

CREATE INDEX ix_verified_pincodes_lookup ON verified_pincodes(pincode, active);
