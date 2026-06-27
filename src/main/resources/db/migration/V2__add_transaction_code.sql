CREATE TABLE IF NOT EXISTS daily_sequences (
                                               sequence_date DATE PRIMARY KEY,
                                               current_value BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS transaction_code VARCHAR(100);

WITH numbered_tx AS (
    SELECT
        id,
        'VP-' || TO_CHAR(created_at, 'YYYYMMDD') || '-' || LPAD(CAST(ROW_NUMBER() OVER (PARTITION BY DATE(created_at) ORDER BY id) AS VARCHAR), 6, '0') as generated_code
    FROM transactions
)
UPDATE transactions t
SET transaction_code = n.generated_code
    FROM numbered_tx n
WHERE t.id = n.id;

INSERT INTO daily_sequences (sequence_date, current_value)
SELECT DATE(created_at), COUNT(*)
FROM transactions
GROUP BY DATE(created_at)
ON CONFLICT (sequence_date) DO UPDATE
                                   SET current_value = GREATEST(daily_sequences.current_value, EXCLUDED.current_value);

ALTER TABLE transactions ALTER COLUMN transaction_code SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'transactions_transaction_code_key'
    ) THEN
ALTER TABLE transactions
    ADD CONSTRAINT transactions_transaction_code_key UNIQUE (transaction_code);
END IF;
END $$;