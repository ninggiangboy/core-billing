-- Wallets, the append-only ledger, reservations and charges.
--
-- The ledger is the financial source of truth. wallets.balance, held and last_sequence are projections that
-- change in the same transaction as the entry that explains them, and a verify job recomputes them. Amounts
-- are signed minor units: credits positive, debits negative, a reversal the negation of what it corrects.

create table wallets (
    id            uuid primary key,
    tenant_id     uuid        not null references tenants (id),
    customer_id   uuid        not null,
    currency      char(3)     not null,
    mode          text        not null check (mode in ('PREPAID', 'POSTPAID')),
    credit_limit  bigint      not null check (credit_limit >= 0),
    balance       bigint      not null,
    held          bigint      not null check (held >= 0),
    last_sequence bigint      not null check (last_sequence >= 0),
    created_at    timestamptz not null,
    unique (tenant_id, id),
    -- A ledger entry references (wallet, currency), so an entry in another currency cannot be inserted.
    unique (tenant_id, id, currency),
    -- A customer has one wallet per currency; a wallet's currency never changes.
    unique (tenant_id, customer_id, currency),
    foreign key (tenant_id, customer_id) references customers (tenant_id, id),
    check (mode = 'POSTPAID' or credit_limit = 0),
    -- Prepaid: available (balance - held) is never negative. Postpaid: never below minus the credit limit.
    check (balance - held >= -credit_limit)
);

call enable_tenant_rls('wallets');

create table reservations (
    id              uuid primary key,
    tenant_id       uuid        not null references tenants (id),
    wallet_id       uuid        not null,
    currency        char(3)     not null,
    amount          bigint      not null check (amount > 0),
    captured_amount bigint      not null default 0 check (captured_amount >= 0 and captured_amount <= amount),
    status          text        not null check (status in ('ACTIVE', 'CAPTURED', 'RELEASED', 'EXPIRED')),
    expires_at      timestamptz not null,
    created_at      timestamptz not null,
    closed_at       timestamptz,
    reference       text,
    idempotency_key text        not null,
    request_hash    text        not null,
    unique (tenant_id, id),
    unique (tenant_id, idempotency_key),
    foreign key (tenant_id, wallet_id, currency) references wallets (tenant_id, id, currency),
    check ((status = 'ACTIVE') = (closed_at is null))
);

create index reservations_expiry_idx on reservations (expires_at) where status = 'ACTIVE';
call enable_tenant_rls('reservations');

create table charges (
    id              uuid primary key,
    tenant_id       uuid        not null references tenants (id),
    wallet_id       uuid        not null,
    currency        char(3)     not null,
    amount          bigint      not null check (amount > 0),
    source          text        not null check (source in ('USAGE', 'RESERVATION', 'STANDALONE')),
    status          text        not null check (status in ('SUCCEEDED', 'FAILED')),
    failure_reason  text,
    reservation_id  uuid,
    usage_ref       text,
    reference       text,
    idempotency_key text        not null,
    request_hash    text        not null,
    created_at      timestamptz not null,
    unique (tenant_id, id),
    unique (tenant_id, idempotency_key),
    foreign key (tenant_id, wallet_id, currency) references wallets (tenant_id, id, currency),
    foreign key (tenant_id, reservation_id) references reservations (tenant_id, id),
    check ((status = 'FAILED') = (failure_reason is not null))
);

call enable_tenant_rls('charges');

create table ledger_entries (
    id              uuid primary key,
    tenant_id       uuid        not null references tenants (id),
    wallet_id       uuid        not null,
    currency        char(3)     not null,
    -- The order of entries within a wallet. It is assigned while the wallet row is locked, so it is gap-free.
    seq             bigint      not null check (seq >= 1),
    type            text        not null check (type in ('CREDIT', 'DEBIT', 'REVERSAL')),
    amount          bigint      not null check (amount <> 0),
    reference       text        not null,
    reservation_id  uuid,
    charge_id       uuid,
    reversal_of     uuid,
    actor           text        not null,
    reason          text,
    idempotency_key text        not null,
    created_at      timestamptz not null,
    unique (wallet_id, seq),
    unique (tenant_id, idempotency_key),
    unique (tenant_id, id),
    foreign key (tenant_id, wallet_id, currency) references wallets (tenant_id, id, currency),
    foreign key (tenant_id, reservation_id) references reservations (tenant_id, id),
    foreign key (tenant_id, charge_id) references charges (tenant_id, id),
    foreign key (tenant_id, reversal_of) references ledger_entries (tenant_id, id),
    check ((type = 'CREDIT' and amount > 0) or (type = 'DEBIT' and amount < 0) or type = 'REVERSAL'),
    check ((type = 'REVERSAL') = (reversal_of is not null))
);

-- An entry can be reversed once.
create unique index ledger_entries_reversal_idx on ledger_entries (reversal_of) where reversal_of is not null;
create index ledger_entries_charge_idx on ledger_entries (charge_id) where charge_id is not null;
call enable_tenant_rls('ledger_entries');

-- The ledger is append-only. The application role may insert and read only; the trigger stops everyone else,
-- including the table owner, from updating, deleting or truncating an entry.
revoke update, delete on ledger_entries from corebilling_app;

create function ledger_entries_append_only() returns trigger
    language plpgsql
as $$
begin
    raise exception 'ledger_entries is append-only: % is not allowed', tg_op using errcode = 'restrict_violation';
end
$$;

create trigger ledger_entries_no_change
    before update or delete on ledger_entries
    for each row execute function ledger_entries_append_only();

create trigger ledger_entries_no_truncate
    before truncate on ledger_entries
    for each statement execute function ledger_entries_append_only();
