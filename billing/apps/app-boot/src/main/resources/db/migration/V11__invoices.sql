-- Invoices: requests for payment of what a wallet already owes.
--
-- An invoice lists charges that were already taken from the wallet (for a Postpaid wallet that is the debt), so
-- paying one credits the wallet and never changes a charge. Every line is backed by a succeeded charge, and a
-- charge appears on at most one line. A line with no invoice is an unbilled line: a Postpaid charge waiting for
-- its aggregation cycle to close.

create table invoices (
    id           uuid primary key,
    tenant_id    uuid        not null references tenants (id),
    customer_id  uuid        not null,
    wallet_id    uuid        not null,
    currency     char(3)     not null,
    number       text,
    origin       text        not null check (origin in ('POSTPAID_CYCLE', 'CHARGE')),
    status       text        not null check (status in ('DRAFT', 'OPEN', 'PAID', 'VOID', 'UNCOLLECTIBLE')),
    total        bigint      not null check (total > 0),
    period_start timestamptz,
    period_end   timestamptz,
    settlement   text        check (settlement in ('WALLET', 'PAYMENT')),
    void_reason  text        check (length(void_reason) <= 500),
    created_at   timestamptz not null,
    issued_at    timestamptz,
    paid_at      timestamptz,
    voided_at    timestamptz,
    unique (tenant_id, id),
    -- Numbers are unique per tenant; a draft has none yet.
    unique (tenant_id, number),
    foreign key (tenant_id, wallet_id, currency) references wallets (tenant_id, id, currency),
    foreign key (tenant_id, customer_id) references customers (tenant_id, id),
    check ((status = 'DRAFT') = (number is null)),
    check ((status = 'PAID') = (paid_at is not null)),
    check ((status = 'PAID') = (settlement is not null)),
    check ((status = 'VOID') = (voided_at is not null and void_reason is not null)),
    check ((period_start is null) = (period_end is null)),
    check (period_end is null or period_end > period_start)
);

-- One invoice per Postpaid wallet and aggregation period, so running the cycle twice cannot bill a period twice.
create unique index invoices_cycle_idx on invoices (tenant_id, wallet_id, period_start) where origin = 'POSTPAID_CYCLE';
create index invoices_customer_idx on invoices (customer_id, id desc);
create index invoices_wallet_idx on invoices (wallet_id, id desc);
create index invoices_status_idx on invoices (status, id desc);
call enable_tenant_rls('invoices');

create table invoice_items (
    id              uuid primary key,
    tenant_id       uuid        not null references tenants (id),
    invoice_id      uuid,
    customer_id     uuid        not null,
    wallet_id       uuid        not null,
    currency        char(3)     not null,
    source          text        not null check (source in ('CHARGE', 'SUBSCRIPTION_PERIOD')),
    charge_id       uuid        not null,
    subscription_id uuid,
    description     text        not null check (length(description) between 1 and 500),
    amount          bigint      not null check (amount > 0),
    -- The period a subscription line covers.
    period_start    timestamptz,
    period_end      timestamptz,
    -- The aggregation period an unbilled line belongs to. Only lines collected for a cycle have one.
    cycle_start     timestamptz,
    cycle_end       timestamptz,
    incurred_at     timestamptz not null,
    unique (tenant_id, id),
    -- A charge is billed on at most one line.
    unique (tenant_id, charge_id),
    foreign key (tenant_id, invoice_id) references invoices (tenant_id, id),
    foreign key (tenant_id, wallet_id, currency) references wallets (tenant_id, id, currency),
    foreign key (tenant_id, charge_id) references charges (tenant_id, id),
    check ((source = 'SUBSCRIPTION_PERIOD') = (subscription_id is not null)),
    check ((period_start is null) = (period_end is null)),
    check ((cycle_start is null) = (cycle_end is null))
);

create index invoice_items_invoice_idx on invoice_items (invoice_id) where invoice_id is not null;
-- The cycle job finds closed periods that still hold unbilled lines.
create index invoice_items_unbilled_idx on invoice_items (wallet_id, cycle_end) where invoice_id is null;
call enable_tenant_rls('invoice_items');

-- How an invoice was, or was not, paid: one row per payment started for it.
create table invoice_payments (
    payment_id      uuid primary key,
    tenant_id       uuid        not null references tenants (id),
    invoice_id      uuid        not null,
    currency        char(3)     not null,
    amount          bigint      not null check (amount > 0),
    status          text        not null check (status in ('STARTED', 'SUCCEEDED', 'FAILED')),
    failure_reason  text        check (length(failure_reason) <= 500),
    ledger_entry_id uuid,
    started_at      timestamptz not null,
    settled_at      timestamptz,
    foreign key (tenant_id, invoice_id) references invoices (tenant_id, id),
    foreign key (tenant_id, payment_id) references payments (tenant_id, id),
    check ((status = 'STARTED') = (settled_at is null)),
    check ((status = 'SUCCEEDED') = (ledger_entry_id is not null))
);

create index invoice_payments_invoice_idx on invoice_payments (invoice_id);
call enable_tenant_rls('invoice_payments');

-- The last invoice number issued per tenant. Issuing locks this row, which is what keeps numbers gap-free.
create table invoice_counters (
    tenant_id   uuid   primary key references tenants (id),
    last_number bigint not null check (last_number >= 1)
);

call enable_tenant_rls('invoice_counters');
