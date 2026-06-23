-- Add public_id columns as UUID with gen_random_uuid() default for new rows
ALTER TABLE users ADD COLUMN IF NOT EXISTS public_id UUID DEFAULT gen_random_uuid();
ALTER TABLE wallets ADD COLUMN IF NOT EXISTS public_id UUID DEFAULT gen_random_uuid();

-- Populate public_id for any existing rows where it might be null
UPDATE users SET public_id = gen_random_uuid() WHERE public_id IS NULL;
UPDATE wallets SET public_id = gen_random_uuid() WHERE public_id IS NULL;

-- Enforce NOT NULL constraints
ALTER TABLE users ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE wallets ALTER COLUMN public_id SET NOT NULL;

-- Enforce uniqueness (this also creates the necessary unique index)
ALTER TABLE users ADD CONSTRAINT users_public_id_key UNIQUE (public_id);
ALTER TABLE wallets ADD CONSTRAINT wallets_public_id_key UNIQUE (public_id);
