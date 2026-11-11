-- Outbound webhooks: endpoints, deliveries with their attempt history, and the consumer inbox.
--
-- A delivery stores the exact body that is signed and sent, so a retry or a resend is byte-identical.
-- An in-flight delivery keeps status IN_PROGRESS with next_attempt_at set to the end of its lease: a worker
-- that dies mid-send simply lets the lease run out and the row becomes due again.

alter table outbox add column last_error text;

-- Each in-process consumer records the events it handled; the key makes the at-least-once relay safe.
create table event_inbox (
    tenant_id    uuid        not null references tenants (id),
    consumer     text        not null,
    event_id     uuid        not null,
    processed_at timestamptz not null default now(),
    primary key (tenant_id, consumer, event_id)
);

call enable_tenant_rls('event_inbox');

create table webhook_endpoints (
    id                uuid primary key,
    tenant_id         uuid        not null references tenants (id),
    url               text        not null check (length(url) between 1 and 2048),
    description       text        check (length(description) <= 500),
    event_types       text[]      not null check (cardinality(event_types) between 1 and 100),
    status            text        not null check (status in ('ACTIVE', 'DISABLED')),
    -- The signing secret is stored only as envelope-encrypted ciphertext and is never returned after creation.
    key_id            text        not null,
    secret_ciphertext text        not null,
    created_at        timestamptz not null,
    updated_at        timestamptz not null,
    secret_rotated_at timestamptz,
    unique (tenant_id, id)
);

call enable_tenant_rls('webhook_endpoints');

create table webhook_deliveries (
    id               uuid primary key,
    tenant_id        uuid        not null references tenants (id),
    endpoint_id      uuid        not null,
    event_id         uuid        not null,
    event_type       text        not null,
    body             text        not null,
    status           text        not null check (status in ('PENDING', 'IN_PROGRESS', 'SUCCEEDED', 'DEAD')),
    attempts         integer     not null default 0 check (attempts >= 0),
    next_attempt_at  timestamptz not null,
    resend_of        uuid,
    last_status_code integer,
    last_error       text,
    created_at       timestamptz not null,
    completed_at     timestamptz,
    unique (tenant_id, id),
    foreign key (tenant_id, endpoint_id) references webhook_endpoints (tenant_id, id),
    foreign key (tenant_id, resend_of) references webhook_deliveries (tenant_id, id),
    check ((status in ('SUCCEEDED', 'DEAD')) = (completed_at is not null))
);

-- One original delivery per event and endpoint; resends are additional rows pointing at the original.
create unique index webhook_deliveries_event_idx on webhook_deliveries (endpoint_id, event_id) where resend_of is null;
create index webhook_deliveries_due_idx on webhook_deliveries (next_attempt_at) where status in ('PENDING', 'IN_PROGRESS');
create index webhook_deliveries_endpoint_idx on webhook_deliveries (endpoint_id, id desc);
create index webhook_deliveries_status_idx on webhook_deliveries (status, id desc);
call enable_tenant_rls('webhook_deliveries');

-- The attempt log is append-only: what was tried and what came back is evidence.
create table webhook_delivery_attempts (
    id               uuid primary key,
    tenant_id        uuid        not null references tenants (id),
    delivery_id      uuid        not null,
    attempt_no       integer     not null check (attempt_no >= 1),
    status_code      integer,
    error            text,
    response_excerpt text,
    latency_ms       integer     not null check (latency_ms >= 0),
    attempted_at     timestamptz not null,
    unique (delivery_id, attempt_no),
    foreign key (tenant_id, delivery_id) references webhook_deliveries (tenant_id, id)
);

call enable_tenant_rls('webhook_delivery_attempts');
revoke update, delete on webhook_delivery_attempts from corebilling_app;
