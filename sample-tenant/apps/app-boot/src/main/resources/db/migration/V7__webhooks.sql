CREATE TABLE webhook_events (
    id               uuid         PRIMARY KEY,
    billing_event_id varchar(64)  NOT NULL,
    type             varchar(80)  NOT NULL,
    tenant_id        varchar(64),
    occurred_at      timestamptz,
    payload          text         NOT NULL,
    received_at      timestamptz  NOT NULL,
    status           varchar(20)  NOT NULL,
    decision         varchar(40),
    detail           varchar(300)
);

CREATE UNIQUE INDEX webhook_events_billing_event_key ON webhook_events (billing_event_id);

CREATE TABLE customer_debts (
    id                  uuid         PRIMARY KEY,
    billing_customer_id varchar(64)  NOT NULL,
    minor_units         bigint       NOT NULL,
    currency            varchar(3)   NOT NULL,
    reason              varchar(200),
    occurred_at         timestamptz  NOT NULL,
    cleared_at          timestamptz
);

CREATE INDEX customer_debts_customer_idx ON customer_debts (billing_customer_id, cleared_at);

CREATE TABLE customer_periods (
    billing_customer_id varchar(64)  PRIMARY KEY,
    period_end          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL
);
