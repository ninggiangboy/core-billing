-- Pricing plans with immutable versions, recorded usage, and the ratings that turn usage into charges.
--
-- Plan versions and usage events are append-only: a charge that cites a plan version, or a usage event that was
-- rated, must read the same tomorrow as it did when it was written. Unit prices and quantities are exact
-- decimals; only the total of a rating is converted to whole minor units, once, by the version's rounding policy.

-- The unit each metric is measured in, fixed by the first plan that uses it. Usage is validated against it.
create table pricing_metrics (
    tenant_id  uuid        not null references tenants (id),
    code       text        not null check (code ~ '^[a-z][a-z0-9._-]{0,63}$'),
    unit       text        not null check (unit ~ '^[A-Za-z][A-Za-z0-9._/-]{0,31}$'),
    created_at timestamptz not null,
    primary key (tenant_id, code)
);

call enable_tenant_rls('pricing_metrics');

create table pricing_plans (
    id         uuid primary key,
    tenant_id  uuid        not null references tenants (id),
    code       text        not null check (code ~ '^[a-z0-9][a-z0-9._-]{0,63}$'),
    name       text        not null check (length(name) between 1 and 200),
    model      text        not null check (model in ('TIME_BASED', 'USAGE_BASED', 'WORKLOAD_BASED', 'EVENT_BASED', 'SUBSCRIPTION', 'HYBRID')),
    currency   char(3)     not null,
    status     text        not null check (status in ('ACTIVE', 'RETIRED')),
    created_at timestamptz not null,
    retired_at timestamptz,
    unique (tenant_id, id),
    unique (tenant_id, code),
    check ((status = 'RETIRED') = (retired_at is not null))
);

call enable_tenant_rls('pricing_plans');

create table pricing_plan_versions (
    plan_id       uuid        not null,
    version       integer     not null check (version >= 1),
    tenant_id     uuid        not null references tenants (id),
    rule          jsonb       not null,
    billing_cycle text        check (billing_cycle in ('DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    min_charge    bigint      check (min_charge >= 0),
    max_charge    bigint      check (max_charge >= 0),
    rounding      text        not null check (rounding in ('HALF_UP', 'HALF_EVEN', 'UP', 'DOWN')),
    valid_from    timestamptz not null,
    valid_until   timestamptz,
    created_at    timestamptz not null,
    created_by    text        not null,
    primary key (plan_id, version),
    unique (tenant_id, plan_id, version),
    foreign key (tenant_id, plan_id) references pricing_plans (tenant_id, id),
    check (valid_until is null or valid_until > valid_from),
    check (min_charge is null or max_charge is null or max_charge >= min_charge)
);

call enable_tenant_rls('pricing_plan_versions');
revoke update, delete on pricing_plan_versions from billing_app;

create function immutable_rows() returns trigger
    language plpgsql
as $$
begin
    raise exception '% is append-only: % is not allowed', tg_table_name, tg_op using errcode = 'restrict_violation';
end
$$;

create trigger pricing_plan_versions_no_change
    before update or delete on pricing_plan_versions
    for each row execute function immutable_rows();

create table usage_events (
    id               uuid primary key,
    tenant_id        uuid          not null references tenants (id),
    customer_id      uuid          not null,
    metric           text          not null check (metric ~ '^[a-z][a-z0-9._-]{0,63}$'),
    quantity         numeric(24, 6) not null check (quantity > 0),
    unit             text          not null,
    occurred_at      timestamptz   not null,
    recorded_at      timestamptz   not null,
    idempotency_key  text          not null,
    request_hash     text          not null,
    -- Where the usage came from: which tenant system reported it, for which of its resources, under which of its event ids.
    tenant_system_id uuid,
    resource_ref     text,
    source_event_id  text,
    unique (tenant_id, id),
    unique (tenant_id, idempotency_key),
    foreign key (tenant_id, customer_id) references customers (tenant_id, id)
);

create index usage_events_aggregate_idx on usage_events (tenant_id, customer_id, metric, occurred_at);
call enable_tenant_rls('usage_events');
revoke update, delete on usage_events from billing_app;

create trigger usage_events_no_change
    before update or delete on usage_events
    for each row execute function immutable_rows();

-- One rating per attempt to charge a set of usage events. set_key identifies the set (a digest of the event ids);
-- attempt counts the earlier attempts that failed, so a retry after a top-up gets a fresh money idempotency key.
create table usage_ratings (
    id             uuid primary key,
    tenant_id      uuid        not null references tenants (id),
    customer_id    uuid        not null,
    wallet_id      uuid        not null,
    plan_id        uuid        not null,
    plan_version   integer     not null,
    set_key        text        not null,
    attempt        integer     not null check (attempt >= 1),
    period_start   timestamptz not null,
    period_end     timestamptz not null,
    usage_count    integer     not null check (usage_count >= 1),
    amount         bigint      not null check (amount >= 0),
    currency       char(3)     not null,
    status         text        not null check (status in ('CHARGED', 'FAILED', 'NO_CHARGE')),
    charge_id      uuid,
    failure_reason text,
    breakdown      jsonb       not null,
    created_at     timestamptz not null,
    unique (tenant_id, id),
    unique (tenant_id, set_key, attempt),
    foreign key (tenant_id, plan_id, plan_version) references pricing_plan_versions (tenant_id, plan_id, version),
    foreign key (tenant_id, customer_id) references customers (tenant_id, id),
    foreign key (tenant_id, wallet_id) references wallets (tenant_id, id),
    foreign key (tenant_id, charge_id) references charges (tenant_id, id),
    -- A rating that came to zero has no charge; a failed one has a (failed) charge and says why.
    check ((status = 'NO_CHARGE') = (charge_id is null)),
    check ((status = 'FAILED') = (failure_reason is not null))
);

call enable_tenant_rls('usage_ratings');

-- A usage event appears here at most once, which is what makes it impossible to charge twice.
-- Only ratings that were charged (or came to nothing) link their events; a failed charge leaves them free to retry.
create table usage_charges (
    usage_event_id uuid primary key,
    tenant_id      uuid not null references tenants (id),
    rating_id      uuid not null,
    foreign key (tenant_id, usage_event_id) references usage_events (tenant_id, id),
    foreign key (tenant_id, rating_id) references usage_ratings (tenant_id, id)
);

create index usage_charges_rating_idx on usage_charges (rating_id);
call enable_tenant_rls('usage_charges');
