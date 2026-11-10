-- Billing Customers and the mapping from a Tenant System's own users to them.

create table customers (
    id           uuid primary key,
    tenant_id    uuid        not null references tenants (id),
    display_name text        not null check (length(display_name) between 1 and 200),
    email        text,
    status       text        not null check (status in ('ACTIVE', 'DEACTIVATED')),
    created_at   timestamptz not null,
    unique (tenant_id, id)
);

call enable_tenant_rls('customers');

-- One external user of one tenant system maps to exactly one customer; the same external id in another
-- tenant (or tenant system) is a different mapping. Both references are composite, so a mapping can never
-- join a customer to another tenant's tenant system.
create table customer_mappings (
    tenant_id        uuid not null,
    tenant_system_id uuid not null,
    external_user_id text not null check (length(external_user_id) between 1 and 255),
    customer_id      uuid not null,
    primary key (tenant_id, tenant_system_id, external_user_id),
    foreign key (tenant_id, tenant_system_id) references tenant_systems (tenant_id, id),
    foreign key (tenant_id, customer_id) references customers (tenant_id, id)
);

create index customer_mappings_customer_idx on customer_mappings (customer_id);
call enable_tenant_rls('customer_mappings');
