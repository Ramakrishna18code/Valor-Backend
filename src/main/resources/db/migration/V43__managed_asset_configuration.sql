CREATE TABLE managed_asset_options (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(40) NOT NULL,
    code VARCHAR(80) NOT NULL,
    label VARCHAR(160) NOT NULL,
    description VARCHAR(500) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_managed_asset_option UNIQUE (category, code)
);
CREATE INDEX ix_managed_asset_options_category ON managed_asset_options(category, active, display_order, id);
