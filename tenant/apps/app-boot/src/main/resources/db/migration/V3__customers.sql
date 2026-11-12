CREATE TABLE customers (
    id                   uuid         PRIMARY KEY,
    user_id              uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    billing_customer_id  varchar(64),
    display_name         varchar(200) NOT NULL,
    email                varchar(320) NOT NULL,
    status               varchar(20)  NOT NULL,
    created_at           timestamptz  NOT NULL
);

CREATE UNIQUE INDEX customers_user_key ON customers (user_id);
