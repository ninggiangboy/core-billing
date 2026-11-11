CREATE TABLE usage_records (
    id                  uuid         PRIMARY KEY,
    order_id            uuid         NOT NULL REFERENCES orders (id),
    billing_customer_id varchar(64)  NOT NULL,
    metric              varchar(80)  NOT NULL,
    quantity            varchar(50)  NOT NULL,
    unit                varchar(30)  NOT NULL,
    billing_event_id    varchar(64)  NOT NULL,
    idempotency_key     varchar(200) NOT NULL,
    occurred_at         timestamptz  NOT NULL,
    recorded_at         timestamptz  NOT NULL
);

CREATE UNIQUE INDEX usage_records_order_metric_key ON usage_records (order_id, metric);
CREATE UNIQUE INDEX usage_records_idempotency_key ON usage_records (idempotency_key);
CREATE INDEX usage_records_customer_idx ON usage_records (billing_customer_id, occurred_at DESC);
