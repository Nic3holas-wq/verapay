CREATE TABLE IF NOT EXISTS user_devices (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_fingerprint VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    location_geo VARCHAR(100),
    user_agent TEXT NOT NULL,
    is_trusted BOOLEAN DEFAULT FALSE,
    last_login TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX idx_devices_user ON user_devices(user_id);
CREATE INDEX idx_devices_fingerprint ON user_devices(device_fingerprint);

CREATE TABLE IF NOT EXISTS transaction_risk_logs (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rules_triggered TEXT[],
    ml_risk_score DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    action_taken VARCHAR(50) NOT NULL,
    is_false_positive BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX idx_risk_tx ON transaction_risk_logs(transaction_id);
CREATE INDEX idx_risk_user ON transaction_risk_logs(user_id);

CREATE TABLE IF NOT EXISTS account_cooldowns (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    modification_type VARCHAR(50) NOT NULL,
    cooldown_expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX idx_cooldown_user ON account_cooldowns(user_id) WHERE is_active = TRUE;