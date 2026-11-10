-- A webhook endpoint belongs to the Tenant System that registered it, or to the Tenant itself when a Tenant User
-- registered it. The owner decides which caller may read, change, rotate or resend its deliveries.

alter table webhook_endpoints add column tenant_system_id uuid;

alter table webhook_endpoints
    add constraint webhook_endpoints_tenant_system_fk
    foreign key (tenant_id, tenant_system_id) references tenant_systems (tenant_id, id);

create index webhook_endpoints_owner_idx on webhook_endpoints (tenant_id, tenant_system_id);
