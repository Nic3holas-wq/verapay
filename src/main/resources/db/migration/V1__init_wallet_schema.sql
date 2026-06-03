CREATE TABLE IF NOT EXISTS users(
                                    id BIGSERIAL PRIMARY KEY,
                                    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone_number VARCHAR(15) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
    );

CREATE TABLE IF NOT EXISTS wallets(
                                      id BIGSERIAL PRIMARY KEY,
                                      owner_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(10) NOT NULL DEFAULT 'KES',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
    );

CREATE TABLE IF NOT EXISTS transactions(
                                           id BIGSERIAL PRIMARY KEY,
                                           from_wallet_id BIGINT REFERENCES wallets(id),
    to_wallet_id BIGINT NOT NULL REFERENCES wallets(id),
    amount DECIMAL(19,4) NOT NULL,
    type VARCHAR(20) NOT NULL, -- TRANSFER, DEPOSIT, WITHDRAW
    status VARCHAR(20) NOT NULL, --PENDING, SUCCESS, FAILED, REVERSED
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    transaction_ref VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_different_wallets CHECK (from_wallet_id != to_wallet_id)
    );

CREATE TABLE IF NOT EXISTS ledger_entries(
                                             id BIGSERIAL PRIMARY KEY,
                                             wallet_id BIGINT NOT NULL REFERENCES wallets(id),
    transaction_id BIGINT NOT NULL REFERENCES transactions(id),
    amount DECIMAL(19,4) NOT NULL,
    balance_after DECIMAL(19,4) NOT NULL,
    entry_type VARCHAR(20) NOT NULL, --DEBIT, CREDIT
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- Users
CREATE UNIQUE INDEX idx_users_email       ON users(email);
CREATE UNIQUE INDEX idx_users_phone       ON users(phone_number);

-- Wallets
CREATE UNIQUE INDEX idx_wallets_owner     ON wallets(owner_id);

-- Transactions
CREATE INDEX idx_tx_from_wallet           ON transactions(from_wallet_id);
CREATE INDEX idx_tx_to_wallet             ON transactions(to_wallet_id);
CREATE INDEX idx_tx_created               ON transactions(created_at DESC);
CREATE UNIQUE INDEX idx_tx_idempotency    ON transactions(idempotency_key);
CREATE UNIQUE INDEX idx_tx_ref            ON transactions(transaction_ref);

-- Ledger
CREATE INDEX idx_ledger_wallet            ON ledger_entries(wallet_id);
CREATE INDEX idx_ledger_transaction       ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_created           ON ledger_entries(created_at DESC);
