-- Identity and access: Billing Users, Hybrid RBAC, Tenant Systems and API credentials.
-- Every table below is tenant-scoped (the Platform is the reserved tenant), has row-level security,
-- and references its parents through composite (tenant_id, id) foreign keys, so a row can never point
-- at another tenant's user, role or tenant system.

-- The permission catalog is defined in code and mirrored here at start-up so grants can reference it.
-- It is global reference data, not tenant data.
create table permissions (
    code text primary key
);

create table billing_users (
    id               uuid primary key,
    tenant_id        uuid        not null references tenants (id),
    scope            text        not null check (scope in ('PLATFORM', 'TENANT')),
    email            text        not null check (email = lower(email)),
    display_name     text        not null,
    keycloak_subject text        not null unique,
    status           text        not null check (status in ('ACTIVE', 'DISABLED')),
    created_at       timestamptz not null,
    unique (tenant_id, id)
);

create unique index billing_users_email_idx on billing_users (tenant_id, email);
call enable_tenant_rls('billing_users');

create table roles (
    id         uuid primary key,
    tenant_id  uuid        not null references tenants (id),
    scope      text        not null check (scope in ('PLATFORM', 'TENANT')),
    name       text        not null,
    is_system  boolean     not null,
    created_at timestamptz not null,
    unique (tenant_id, id)
);

create unique index roles_name_idx on roles (tenant_id, lower(name));
call enable_tenant_rls('roles');

create table role_permissions (
    tenant_id       uuid not null,
    role_id         uuid not null,
    permission_code text not null references permissions (code),
    primary key (role_id, permission_code),
    foreign key (tenant_id, role_id) references roles (tenant_id, id) on delete cascade
);

call enable_tenant_rls('role_permissions');

create table role_assignments (
    tenant_id uuid not null,
    user_id   uuid not null,
    role_id   uuid not null,
    primary key (user_id, role_id),
    foreign key (tenant_id, user_id) references billing_users (tenant_id, id) on delete cascade,
    foreign key (tenant_id, role_id) references roles (tenant_id, id)
);

call enable_tenant_rls('role_assignments');

create table user_permissions (
    tenant_id       uuid not null,
    user_id         uuid not null,
    permission_code text not null references permissions (code),
    primary key (user_id, permission_code),
    foreign key (tenant_id, user_id) references billing_users (tenant_id, id) on delete cascade
);

call enable_tenant_rls('user_permissions');

create table tenant_systems (
    id         uuid primary key,
    tenant_id  uuid        not null references tenants (id),
    name       text        not null,
    status     text        not null check (status in ('ACTIVE', 'DISABLED')),
    created_at timestamptz not null,
    unique (tenant_id, id)
);

create unique index tenant_systems_name_idx on tenant_systems (tenant_id, lower(name));
call enable_tenant_rls('tenant_systems');

-- Only the SHA-256 digest of the secret is stored. key_id is unique across the platform so a presented
-- key can never be ambiguous; row-level security still hides other tenants' keys from a lookup.
create table api_credentials (
    id               uuid primary key,
    tenant_id        uuid        not null references tenants (id),
    tenant_system_id uuid        not null,
    key_id           text        not null unique,
    secret_hash      bytea       not null check (octet_length(secret_hash) = 32),
    scopes           text[]      not null,
    label            text        not null,
    status           text        not null check (status in ('ACTIVE', 'REVOKED')),
    created_at       timestamptz not null,
    expires_at       timestamptz,
    revoked_at       timestamptz,
    unique (tenant_id, id),
    foreign key (tenant_id, tenant_system_id) references tenant_systems (tenant_id, id)
);

create index api_credentials_system_idx on api_credentials (tenant_system_id);
call enable_tenant_rls('api_credentials');
