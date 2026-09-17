ALTER TABLE strategy_config
    ALTER COLUMN params_hash TYPE VARCHAR(64)
    USING TRIM(params_hash);
