-- Billing Sessions and the Customer Portal.
--
-- A session is a time-limited, scoped grant for one Customer. The exchange token is single-use and only its
-- SHA-256 digest is stored; the exchange replaces it with a separate cookie token, also stored as a digest. The
-- portal return-URL allowlist keeps a session from sending the customer to another site. Stripe Customers are
-- created lazily for the hosted payment-method flow and belong to the tenant's Stripe configuration.

create table portal_return_urls (
    tenant_id uuid not null references tenants (id),
    url       text not null check (url ~ '^https://'),
    primary key (tenant_id, url)
);

call enable_tenant_rls('portal_return_urls');

create table billing_sessions (
    id                  uuid primary key,
    tenant_id           uuid        not null references tenants (id),
    customer_id         uuid        not null,
    tenant_system_id    uuid        not null,
    permissions         text[]      not null check (
        cardinality(permissions) > 0
        and permissions <@ array[
            'session:view', 'customer:view', 'wallet:view', 'ledger:view', 'usage:view', 'charge:view', 'invoice:view',
            'subscription:view', 'payment:view', 'refund:view', 'top-up:create', 'payment-method:manage'
        ]::text[]),
    return_url          text        not null check (length(return_url) between 1 and 2048),
    status              text        not null check (status in ('ACTIVE', 'REVOKED')),
    exchange_token_hash text        not null unique check (length(exchange_token_hash) = 64),
    cookie_token_hash   text        unique check (cookie_token_hash is null or length(cookie_token_hash) = 64),
    created_at          timestamptz not null,
    expires_at          timestamptz not null,
    exchanged_at        timestamptz,
    revoked_at          timestamptz,
    unique (tenant_id, id),
    foreign key (tenant_id, customer_id) references customers (tenant_id, id),
    foreign key (tenant_id, tenant_system_id) references tenant_systems (tenant_id, id),
    check (expires_at > created_at),
    check ((cookie_token_hash is null) = (exchanged_at is null)),
    check ((status = 'REVOKED') = (revoked_at is not null))
);

create index billing_sessions_customer_idx on billing_sessions (customer_id);
create index billing_sessions_system_idx on billing_sessions (tenant_system_id);
create index billing_sessions_expiry_idx on billing_sessions (expires_at) where status = 'ACTIVE';
call enable_tenant_rls('billing_sessions');

create table stripe_customers (
    tenant_id          uuid        not null references tenants (id),
    customer_id        uuid        not null,
    configuration_id   uuid        not null,
    stripe_customer_id text        not null unique,
    created_at         timestamptz not null,
    primary key (tenant_id, customer_id, configuration_id),
    foreign key (tenant_id, customer_id) references customers (tenant_id, id),
    foreign key (tenant_id, configuration_id) references stripe_configurations (tenant_id, id)
);

call enable_tenant_rls('stripe_customers');
