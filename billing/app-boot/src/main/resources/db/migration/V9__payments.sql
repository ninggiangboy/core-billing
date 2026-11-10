-- Payments through the Tenant's own Stripe configuration: configurations, top-ups, refunds, and the processing
-- history of Stripe's webhook events.
--
-- Money reaches the ledger only from a verified Stripe event, so everything Stripe tells us is stored first
-- (stripe_events, keyed by account and event id) and processed afterwards, with every attempt kept as history.

-- Which tenant owns a Stripe configuration or account. A webhook arrives before any tenant is known, so this
-- one table is platform-owned and readable without a tenant (like tenant_domains). It holds no secrets.
create table stripe_routes (
    configuration_id uuid primary key,
    tenant_id        uuid        not null references tenants (id),
    stripe_account_id text       not null unique,
    created_at       timestamptz not null
);

create table stripe_configurations (
    id                       uuid primary key,
    tenant_id                uuid        not null references tenants (id),
    mode                     text        not null check (mode in ('DIRECT', 'CONNECT')),
    account_id               text        not null,
    environment              text        not null check (environment in ('TEST', 'LIVE')),
    -- PENDING: waiting for the webhook secret (Direct) or for onboarding to finish (Connect).
    -- ACTIVE: can take payments. RESTRICTED: linked, but Stripe has not enabled card payments (yet or any more).
    status                   text        not null check (status in ('PENDING', 'ACTIVE', 'RESTRICTED', 'DISCONNECTED')),
    -- Secrets are stored only as envelope-encrypted ciphertext and are never returned by any read API.
    secret_key_ciphertext     text,
    webhook_secret_ciphertext text,
    publishable_key          text,
    card_payments_active     boolean     not null default false,
    created_at               timestamptz not null,
    updated_at               timestamptz not null,
    connected_at             timestamptz,
    last_verified_at         timestamptz,
    disconnected_at          timestamptz,
    unique (tenant_id, id),
    check (mode = 'DIRECT' or (secret_key_ciphertext is null and webhook_secret_ciphertext is null)),
    check (mode = 'CONNECT' or secret_key_ciphertext is not null),
    check ((status = 'DISCONNECTED') = (disconnected_at is not null))
);

-- A Tenant has at most one live configuration; replacing it disconnects the previous one first.
create unique index stripe_configurations_one_active_idx on stripe_configurations (tenant_id) where status <> 'DISCONNECTED';
call enable_tenant_rls('stripe_configurations');

create table payments (
    id                  uuid primary key,
    tenant_id           uuid        not null references tenants (id),
    configuration_id    uuid        not null,
    wallet_id           uuid        not null,
    customer_id         uuid        not null,
    currency            char(3)     not null,
    amount              bigint      not null check (amount > 0),
    -- Money already sent back. Never above the captured amount.
    refunded_amount     bigint      not null default 0 check (refunded_amount >= 0 and refunded_amount <= amount),
    purpose             text        not null check (purpose in ('TOP_UP')),
    status              text        not null check (status in ('REQUIRES_ACTION', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELED')),
    checkout_session_id text,
    payment_intent_id   text,
    checkout_url        text,
    failure_reason      text,
    ledger_entry_id     uuid,
    idempotency_key     text        not null,
    request_hash        text        not null,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    completed_at        timestamptz,
    unique (tenant_id, id),
    unique (tenant_id, idempotency_key),
    foreign key (tenant_id, configuration_id) references stripe_configurations (tenant_id, id),
    foreign key (tenant_id, wallet_id, currency) references wallets (tenant_id, id, currency),
    check ((status in ('SUCCEEDED', 'FAILED', 'CANCELED')) = (completed_at is not null)),
    check ((status = 'SUCCEEDED') = (ledger_entry_id is not null))
);

create unique index payments_checkout_session_idx on payments (checkout_session_id) where checkout_session_id is not null;
create unique index payments_payment_intent_idx on payments (payment_intent_id) where payment_intent_id is not null;
create index payments_wallet_idx on payments (wallet_id, id desc);
create index payments_status_idx on payments (status, id desc);
call enable_tenant_rls('payments');

-- A card payment that did not go through. The customer may try again within the same Checkout Session, so a
-- failed attempt is evidence, not the end of the payment.
create table payment_attempts (
    id              uuid primary key,
    tenant_id       uuid        not null references tenants (id),
    payment_id      uuid        not null,
    stripe_event_id text        not null,
    failure_code    text,
    failure_message text,
    occurred_at     timestamptz not null,
    unique (payment_id, stripe_event_id),
    foreign key (tenant_id, payment_id) references payments (tenant_id, id)
);

call enable_tenant_rls('payment_attempts');
revoke update, delete on payment_attempts from corebilling_app;

create table refunds (
    id                uuid primary key,
    tenant_id         uuid        not null references tenants (id),
    payment_id        uuid        not null,
    currency          char(3)     not null,
    amount            bigint      not null check (amount > 0),
    status            text        not null check (status in ('PENDING', 'SUCCEEDED', 'FAILED')),
    reason            text        check (length(reason) <= 500),
    stripe_refund_id  text,
    -- The unspent balance is held while Stripe works, so the refund cannot drive a prepaid wallet negative.
    reservation_id    uuid,
    ledger_entry_id   uuid,
    failure_reason    text,
    actor             text        not null,
    idempotency_key   text        not null,
    request_hash      text        not null,
    created_at        timestamptz not null,
    updated_at        timestamptz not null,
    completed_at      timestamptz,
    unique (tenant_id, id),
    unique (tenant_id, idempotency_key),
    foreign key (tenant_id, payment_id) references payments (tenant_id, id),
    foreign key (tenant_id, reservation_id) references reservations (tenant_id, id),
    check ((status in ('SUCCEEDED', 'FAILED')) = (completed_at is not null)),
    check ((status = 'SUCCEEDED') = (ledger_entry_id is not null))
);

create unique index refunds_stripe_refund_idx on refunds (stripe_refund_id) where stripe_refund_id is not null;
create index refunds_payment_idx on refunds (payment_id, id);
call enable_tenant_rls('refunds');

-- Every verified Stripe event, stored before it is processed. The unique key makes a replay a no-op.
create table stripe_events (
    id               uuid primary key,
    tenant_id        uuid        not null references tenants (id),
    configuration_id uuid        not null,
    stripe_account   text        not null,
    event_id         text        not null,
    type             text        not null,
    livemode         boolean     not null,
    payload          text        not null,
    status           text        not null check (status in ('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED', 'DEAD')),
    attempts         integer     not null default 0 check (attempts >= 0),
    next_attempt_at  timestamptz,
    last_error       text,
    received_at      timestamptz not null,
    processed_at     timestamptz,
    unique (tenant_id, id),
    unique (stripe_account, event_id),
    foreign key (tenant_id, configuration_id) references stripe_configurations (tenant_id, id),
    check ((status in ('PROCESSED', 'IGNORED', 'DEAD')) = (processed_at is not null))
);

create index stripe_events_due_idx on stripe_events (next_attempt_at) where status = 'FAILED';
create index stripe_events_unprocessed_idx on stripe_events (received_at) where status = 'RECEIVED';
create index stripe_events_status_idx on stripe_events (status, received_at desc, id);
call enable_tenant_rls('stripe_events');

-- What happened on each attempt to process an event: append-only.
create table stripe_event_attempts (
    id           uuid primary key,
    tenant_id    uuid        not null references tenants (id),
    event_row_id uuid        not null,
    attempt_no   integer     not null check (attempt_no >= 1),
    outcome      text        not null check (outcome in ('PROCESSED', 'IGNORED', 'DEFERRED', 'FAILED', 'REJECTED')),
    detail       text,
    attempted_at timestamptz not null,
    unique (event_row_id, attempt_no),
    foreign key (tenant_id, event_row_id) references stripe_events (tenant_id, id)
);

call enable_tenant_rls('stripe_event_attempts');
revoke update, delete on stripe_event_attempts from corebilling_app;

-- Things a person should look at: a webhook whose signature failed, an event for an account we do not know, a
-- payment that disagrees with Stripe. Repeats of the same open finding are counted, not duplicated.
create table stripe_incidents (
    id               uuid primary key,
    tenant_id        uuid        not null references tenants (id),
    configuration_id uuid,
    kind             text        not null check (kind in ('SIGNATURE_REJECTED', 'UNKNOWN_ACCOUNT', 'EVENT_REJECTED', 'RECONCILIATION_MISMATCH')),
    subject          text        not null,
    detail           text        not null,
    occurrences      integer     not null default 1 check (occurrences >= 1),
    created_at       timestamptz not null,
    last_seen_at     timestamptz not null,
    resolved_at      timestamptz,
    foreign key (tenant_id, configuration_id) references stripe_configurations (tenant_id, id)
);

create unique index stripe_incidents_open_idx on stripe_incidents (tenant_id, kind, subject) where resolved_at is null;
create index stripe_incidents_list_idx on stripe_incidents (tenant_id, last_seen_at desc);
call enable_tenant_rls('stripe_incidents');
