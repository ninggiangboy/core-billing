-- Shared technical tables used by every bounded context: outbox, idempotency and audit.

create table outbox (
    id              uuid primary key,
    tenant_id       uuid        not null references tenants (id),
    aggregate       text        not null,
    type            text        not null,
    payload         jsonb       not null,
    created_at      timestamptz not null,
    status          text        not null default 'PENDING' check (status in ('PENDING', 'DELIVERED', 'DEAD')),
    attempts        integer     not null default 0,
    next_attempt_at timestamptz not null
);

create index outbox_pending_idx on outbox (next_attempt_at) where status = 'PENDING';
call enable_tenant_rls('outbox');

-- The first writer inserts the key inside its own transaction; the primary key makes a concurrent
-- duplicate wait for that transaction and then see the stored response.
create table idempotency_keys (
    tenant_id       uuid        not null references tenants (id),
    scope           text        not null,
    key             text        not null,
    request_hash    text        not null,
    response_status integer,
    response_body   text,
    created_at      timestamptz not null default now(),
    primary key (tenant_id, scope, key)
);

call enable_tenant_rls('idempotency_keys');

-- Platform-scope actions carry no tenant. The policy keeps the two scopes apart in both directions.
create table audit_log (
    id          uuid primary key,
    scope       text        not null check (scope in ('PLATFORM', 'TENANT')),
    tenant_id   uuid references tenants (id),
    actor       text        not null,
    action      text        not null,
    subject     text        not null,
    request_id  text,
    occurred_at timestamptz not null,
    check ((scope = 'TENANT') = (tenant_id is not null))
);

create index audit_log_tenant_idx on audit_log (tenant_id, occurred_at);

alter table audit_log enable row level security;
alter table audit_log force row level security;
create policy audit_scope on audit_log
    using ((scope = 'TENANT' and tenant_id = app_tenant_id()) or (scope = 'PLATFORM' and app_tenant_id() is null))
    with check ((scope = 'TENANT' and tenant_id = app_tenant_id()) or (scope = 'PLATFORM' and app_tenant_id() is null));

-- The audit trail is append-only for the application.
revoke update, delete on audit_log from billing_app;
