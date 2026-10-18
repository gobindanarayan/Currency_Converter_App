CREATE DATABASE IF NOT EXISTS currency_converter CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'currency_app'@'localhost' IDENTIFIED BY 'replace_this_password';
GRANT SELECT, INSERT, UPDATE ON currency_converter.* TO 'currency_app'@'localhost';
FLUSH PRIVILEGES;
USE currency_converter;

CREATE TABLE IF NOT EXISTS conversion_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    from_currency CHAR(3) NOT NULL,
    to_currency CHAR(3) NOT NULL,
    amount DECIMAL(20,6) NOT NULL,
    applied_rate DECIMAL(24,12) NOT NULL,
    converted_amount DECIMAL(20,6) NOT NULL,
    rate_source VARCHAR(40) NOT NULL,
    rate_date DATE NULL COMMENT 'Observation date supplied by the rate provider; NULL for manual rates',
    converted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_converted_at (converted_at DESC)
);

CREATE TABLE IF NOT EXISTS app_settings (
    setting_key VARCHAR(50) PRIMARY KEY,
    setting_value VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
