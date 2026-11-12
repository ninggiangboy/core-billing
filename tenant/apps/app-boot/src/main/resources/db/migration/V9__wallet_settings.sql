CREATE TABLE customer_wallet_settings (
    customer_id  uuid        PRIMARY KEY REFERENCES customers (id) ON DELETE CASCADE,
    wallet_mode  varchar(20) NOT NULL,
    currency     varchar(3)  NOT NULL,
    credit_limit bigint,
    wallet_id    varchar(64),
    updated_at   timestamptz NOT NULL
);
