-- Alter transactions table to add fraud-related fields
ALTER TABLE transactions 
ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45),
ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(255),
ADD COLUMN IF NOT EXISTS location_geo VARCHAR(100),
ADD COLUMN IF NOT EXISTS is_suspicious BOOLEAN DEFAULT FALSE;

-- Create known_bad_ips table
CREATE TABLE IF NOT EXISTS known_bad_ips (
    id BIGSERIAL PRIMARY KEY,
    ip_address VARCHAR(45) NOT NULL UNIQUE,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Prepopulate some known-bad IPs/Proxies for testing
INSERT INTO known_bad_ips (ip_address, reason) VALUES 
('192.168.1.100', 'Malicious proxy detected in audit'),
('203.0.113.50', 'Tor exit node')
ON CONFLICT (ip_address) DO NOTHING;

-- Create user_limits table
CREATE TABLE IF NOT EXISTS user_limits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    daily_limit DECIMAL(19,4) NOT NULL DEFAULT 50000.0000,
    monthly_limit DECIMAL(19,4) NOT NULL DEFAULT 250000.0000,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

-- Prepopulate limits for existing users
INSERT INTO user_limits (user_id, daily_limit, monthly_limit)
SELECT id, 50000.0000, 250000.0000 FROM users
ON CONFLICT (user_id) DO NOTHING;
