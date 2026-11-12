CREATE TABLE orders (
    id                     uuid         PRIMARY KEY,
    customer_id            uuid         NOT NULL REFERENCES customers (id),
    billing_customer_id    varchar(64)  NOT NULL,
    wallet_id              varchar(64)  NOT NULL,
    catalog_plan_id        uuid         NOT NULL REFERENCES catalog_plans (id),
    billing_plan_id        varchar(64)  NOT NULL,
    pricing_model          varchar(40)  NOT NULL,
    metric                 varchar(80)  NOT NULL,
    usage_quantity         varchar(50)  NOT NULL,
    unit                   varchar(30)  NOT NULL,
    event_count            bigint,
    estimated_minor_units  bigint       NOT NULL,
    currency               varchar(3)   NOT NULL,
    reservation_id         varchar(64),
    status                 varchar(20)  NOT NULL,
    reference              varchar(80)  NOT NULL,
    failure_reason         varchar(300),
    created_at             timestamptz  NOT NULL,
    updated_at             timestamptz  NOT NULL
);

CREATE UNIQUE INDEX orders_reference_key ON orders (reference);
CREATE INDEX orders_customer_idx ON orders (customer_id, created_at DESC);

CREATE TABLE execution_queue (
    id          uuid        PRIMARY KEY,
    order_id    uuid        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    status      varchar(20) NOT NULL,
    enqueued_at timestamptz NOT NULL,
    claimed_at  timestamptz,
    finished_at timestamptz,
    attempts    integer     NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX execution_queue_order_key ON execution_queue (order_id);
CREATE INDEX execution_queue_pending_idx ON execution_queue (status, enqueued_at);
