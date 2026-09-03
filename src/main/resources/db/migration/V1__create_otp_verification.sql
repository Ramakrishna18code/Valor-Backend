-- Flyway migration: create otp_verification table
CREATE TABLE IF NOT EXISTS otp_verification (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  mobile_number VARCHAR(20) NOT NULL,
  otp_hash VARCHAR(100) NOT NULL,
  attempts_remaining INT NOT NULL,
  max_attempts INT NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  locked_until TIMESTAMP,
  verified BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  verified_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_otp_mobile_number ON otp_verification(mobile_number);
CREATE INDEX IF NOT EXISTS idx_otp_mobile_created_at ON otp_verification(mobile_number, created_at);
CREATE INDEX IF NOT EXISTS idx_otp_mobile_status ON otp_verification(mobile_number, verified, expires_at, locked_until);
