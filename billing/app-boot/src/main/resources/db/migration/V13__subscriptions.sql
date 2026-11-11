-- Recurring subscriptions and their history.
--
-- A subscription is a schedule of period charges counted from an anchor: period n is [anchor + n cycles,
-- anchor + n+1 cycles), charged in advance at its start. periods_charged is how many have been charged, so the next
-- one is due at anchor + periods_charged cycles. Billing only reports renewals and failures; it never provisions
-- or stops anything for the Tenant.

create table subscriptions (
    id                   uuid primary key,
    tenant_id            uuid        not null references tenants (id),
    customer_id          uuid        not null,
    wallet_id            uuid        not null,
    currency             char(3)     not null,
    plan_id              uuid        not null,
    plan_code            text        not null,
    cycle                text        not null check (cycle in ('DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    anchor               timestamptz not null,
    periods_charged      integer     not null check (periods_charged >= 0),
    status               text        not null check (status in ('ACTIVE', 'PAST_DUE', 'CANCELED')),
    cancel_at_period_end boolean     not null,
    canceled_at          timestamptz,
    -- When the next period is due, or the next retry after a failure. Null once canceled.
    next_renewal_at      timestamptz,
    renewal_attempts     integer     not null default 0 check (renewal_attempts >= 0),
    created_at           timestamptz not null,
    updated_at           timestamptz not null,
    idempotency_key      text        not null,
    request_hash         text        not null,
    unique (tenant_id, id),
    unique (tenant_id, idempotency_key),
    foreign key (tenant_id, wallet_id, currency) references wallets (tenant_id, id, currency),
    foreign key (tenant_id, customer_id) references customers (tenant_id, id),
    check ((status = 'CANCELED') = (canceled_at is not null)),
    check ((status = 'CANCELED') = (next_renewal_at is null)),
    check (not cancel_at_period_end or status <> 'CANCELED')
);

-- The renewal job scans for subscriptions that are due.
create index subscriptions_due_idx on subscriptions (next_renewal_at) where status in ('ACTIVE', 'PAST_DUE');
create index subscriptions_customer_idx on subscriptions (customer_id, id desc);
call enable_tenant_rls('subscriptions');

-- What happened to a subscription, oldest first. Append-only.
create table subscription_events (
    id              uuid primary key,
    tenant_id       uuid        not null references tenants (id),
    subscription_id uuid        not null,
    type            text        not null check (type in ('CREATED', 'RENEWED', 'PAYMENT_FAILED', 'CANCEL_REQUESTED', 'CANCELED')),
    period_number   integer     check (period_number >= 0),
    period_start    timestamptz,
    period_end      timestamptz,
    amount          bigint      check (amount > 0),
    charge_id       uuid,
    detail          text        check (length(detail) <= 500),
    occurred_at     timestamptz not null,
    foreign key (tenant_id, subscription_id) references subscriptions (tenant_id, id)
);

create index subscription_events_subscription_idx on subscription_events (subscription_id, occurred_at, id);
call enable_tenant_rls('subscription_events');
