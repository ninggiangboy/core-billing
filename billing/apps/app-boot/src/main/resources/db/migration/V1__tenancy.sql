-- Roles, the row-level-security template and the tenant model.
--
-- The migration owner (the role Flyway connects as) owns every table. The application connects as
-- billing_app, which owns nothing and cannot bypass row-level security, so a mis-wired
-- connection fails closed instead of reading across tenants.

do $$
begin
    if not exists (select from pg_roles where rolname = 'billing_app') then
        create role billing_app login password '${appDbPassword}'
            nosuperuser nocreatedb nocreaterole nobypassrls;
    end if;
end
$$;

grant usage on schema public to billing_app;
alter default privileges in schema public
    grant select, insert, update, delete on tables to billing_app;
alter default privileges in schema public
    grant usage, select on sequences to billing_app;

-- The tenant of the current transaction. set_config(..., true) is transaction-local, and after the
-- first use PostgreSQL reports an unset value as '' rather than NULL, hence the nullif.
create function app_tenant_id() returns uuid
    language sql stable
as $$ select nullif(current_setting('app.tenant_id', true), '')::uuid $$;

-- Every tenant-scoped table calls this once. FORCE applies the policy to the table owner too.
create procedure enable_tenant_rls(tbl regclass)
    language plpgsql
as $$
begin
    execute format('alter table %s enable row level security', tbl);
    execute format('alter table %s force row level security', tbl);
    execute format(
        'create policy tenant_isolation on %s using (tenant_id = app_tenant_id()) with check (tenant_id = app_tenant_id())',
        tbl);
end
$$;

-- Platform-owned tables: readable without a tenant, because the hostname must be resolved to a
-- tenant before any tenant context exists. They hold no financial data.
create table tenants (
    id         uuid primary key,
    code       text        not null unique check (code ~ '^[a-z][a-z0-9-]{1,38}[a-z0-9]$'),
    name       text        not null check (length(name) between 1 and 200),
    status     text        not null check (status in ('ACTIVE', 'DEACTIVATED')),
    created_at timestamptz not null
);

create table tenant_domains (
    id         uuid primary key,
    tenant_id  uuid references tenants (id),
    hostname   text        not null unique check (hostname = lower(hostname)),
    kind       text        not null check (kind in ('PLATFORM', 'TENANT_SUBDOMAIN')),
    created_at timestamptz not null,
    check ((kind = 'PLATFORM') = (tenant_id is null))
);

create index tenant_domains_tenant_idx on tenant_domains (tenant_id);
